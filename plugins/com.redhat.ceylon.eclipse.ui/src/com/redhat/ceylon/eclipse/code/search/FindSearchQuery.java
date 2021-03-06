package com.redhat.ceylon.eclipse.code.search;

import static com.redhat.ceylon.eclipse.core.builder.CeylonBuilder.getCeylonClassesOutputFolder;
import static com.redhat.ceylon.eclipse.core.builder.CeylonBuilder.getProjectTypeChecker;
import static com.redhat.ceylon.eclipse.core.builder.CeylonBuilder.getUnit;
import static com.redhat.ceylon.eclipse.util.DocLinks.nameRegion;
import static com.redhat.ceylon.eclipse.util.EditorUtil.getActivePage;
import static com.redhat.ceylon.eclipse.util.JavaSearch.createSearchPattern;
import static com.redhat.ceylon.eclipse.util.JavaSearch.getProjectsToSearch;
import static com.redhat.ceylon.eclipse.util.JavaSearch.runSearch;
import static com.redhat.ceylon.ide.common.util.toJavaString_.toJavaString;
import static java.util.Arrays.asList;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.internal.ui.search.NewSearchResultCollector;
import org.eclipse.jface.text.IRegion;
import org.eclipse.search.ui.ISearchQuery;
import org.eclipse.search.ui.ISearchResult;
import org.eclipse.search.ui.text.AbstractTextSearchResult;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;

import com.redhat.ceylon.compiler.typechecker.TypeChecker;
import com.redhat.ceylon.compiler.typechecker.context.PhasedUnit;
import com.redhat.ceylon.compiler.typechecker.io.VirtualFile;
import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.eclipse.code.editor.CeylonEditor;
import com.redhat.ceylon.eclipse.code.parse.CeylonParseController;
import com.redhat.ceylon.eclipse.core.builder.CeylonNature;
import com.redhat.ceylon.eclipse.util.Filters;
import com.redhat.ceylon.ide.common.model.BaseIdeModule;
import com.redhat.ceylon.ide.common.model.CeylonBinaryUnit;
import com.redhat.ceylon.ide.common.model.CeylonProject;
import com.redhat.ceylon.ide.common.model.IJavaModelAware;
import com.redhat.ceylon.ide.common.model.IdeModule;
import com.redhat.ceylon.model.typechecker.model.Declaration;
import com.redhat.ceylon.model.typechecker.model.Module;
import com.redhat.ceylon.model.typechecker.model.Modules;
import com.redhat.ceylon.model.typechecker.model.Package;
import com.redhat.ceylon.model.typechecker.model.Referenceable;
import com.redhat.ceylon.model.typechecker.model.Unit;

abstract class FindSearchQuery implements ISearchQuery {
    
    private Referenceable referencedDeclaration;
    //private final IProject project;
    private AbstractTextSearchResult result = 
            new CeylonSearchResult(this);
    private int count = 0;
    private IWorkbenchPage page;
    private String name;
    private IProject project;
    
    FindSearchQuery(Referenceable referencedDeclaration, 
            IProject project) {
        this.referencedDeclaration = referencedDeclaration;
        this.project = project;
        //this.project = project;
        this.page = getActivePage();
        name = referencedDeclaration.getNameAsString();
        if (referencedDeclaration instanceof Declaration) {
            Declaration dec = 
                    (Declaration) 
                        referencedDeclaration;
            if (dec.isClassOrInterfaceMember()) {
                Declaration classOrInterface = 
                        (Declaration) 
                            dec.getContainer();
                name = classOrInterface.getName() 
                        + '.'  + name;
            }
        }
    }
    
    private Filters filters = new Filters();
    
    @Override
    public IStatus run(IProgressMonitor monitor) 
            throws OperationCanceledException {
        monitor.beginTask("Searching for " + 
                labelString() + " '" + name + "'", 
                estimateWork(monitor));
        if (monitor.isCanceled()) {
            return Status.CANCEL_STATUS;
        }
        findCeylonReferences(monitor);
        if (referencedDeclaration instanceof Declaration && 
                project!=null) {
            findJavaReferences(monitor);
        }
        monitor.done();
        referencedDeclaration = null;
        return Status.OK_STATUS;
    }

    private void findCeylonReferences(IProgressMonitor monitor) {
        Set<String> searchedArchives = new HashSet<String>();
        Package pack = getPackage();
        if (pack==null) return;
        List<IProject> projectsToSearch = 
                asList(getProjectsToSearch(this.project));
        for (IProject project: 
                projectsToSearch) {
            if (CeylonNature.isEnabled(project)) {
                TypeChecker typeChecker = 
                        getProjectTypeChecker(project);
                List<PhasedUnit> phasedUnits = 
                        typeChecker.getPhasedUnits()
                            .getPhasedUnits();
                findInUnits(phasedUnits, monitor);
                monitor.worked(1);
                if (monitor.isCanceled()) {
                    throw new OperationCanceledException();
                }
                Modules modules = 
                        typeChecker.getContext()
                            .getModules();
                for (Module m: modules.getListOfModules()) {
                    if (m instanceof IdeModule &&
                            !filters.isFiltered(m)) {
                        IdeModule<IProject, IResource, IFolder, IFile> module = 
                                (IdeModule<IProject, IResource, IFolder, IFile>) m;
                        if (module.getIsCeylonArchive() && 
                                !module.getIsProjectModule() && 
                                module.getArtifact()!=null) {
                            CeylonProject<IProject, IResource, IFolder, IFile> originalProject = 
                                    module.getOriginalProject();
                            if (originalProject != null 
                                    && projectsToSearch.contains(
                                            originalProject.getIdeArtifact())) {
                                continue;
                            }
                           
                            String archivePath = 
                                    module.getArtifact()
                                        .getAbsolutePath();
                            String sourceArchivePath = 
                                    toJavaString(module.getSourceArchivePath());
                            if (searchedArchives.add(archivePath) &&
                                searchedArchives.add(sourceArchivePath) && 
                                    m.getAllReachablePackages()
                                        .contains(pack)) {
                                findInUnits(
                                        module.getPhasedUnitsAsJavaList(), 
                                        monitor);
                                monitor.worked(1);
                                if (monitor.isCanceled()) {
                                    throw new OperationCanceledException();
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private Package getPackage() {
        if (referencedDeclaration instanceof Declaration) {
            return referencedDeclaration.getUnit()
                        .getPackage();
        }
        else if (referencedDeclaration instanceof Package) {
            return (Package) referencedDeclaration;
        }
        else if (referencedDeclaration instanceof Module) {
            Module module = (Module) referencedDeclaration;
            return module.getRootPackage();
        }
        else {
            return null;
        }
    }
    
    private int estimateWork(IProgressMonitor monitor) {
        int work = 0;
        Set<String> searchedArchives = new HashSet<String>();
        Package pack = getPackage();
        if (pack==null) return 0;
        for (IProject project: 
                getProjectsToSearch(this.project)) {
            if (CeylonNature.isEnabled(project)) {
                work++;
                Modules modules = 
                        getProjectTypeChecker(project)
                                .getContext()
                                .getModules();
                for (Module m: modules.getListOfModules()) {
                    if (m instanceof BaseIdeModule &&
                            !filters.isFiltered(m)) {
                        BaseIdeModule module = (BaseIdeModule) m;
                        if (module.getIsCeylonArchive() && 
                                !module.getIsProjectModule() && 
                                module.getArtifact()!=null) { 
                            String archivePath = 
                                    module.getArtifact()
                                        .getAbsolutePath();
                            String sourceArchivePath = 
                                    toJavaString(module.getSourceArchivePath());
                            if (searchedArchives.add(archivePath) &&
                                searchedArchives.add(sourceArchivePath) && 
                                    m.getAllReachablePackages()
                                        .contains(pack)) {
                                work++;
                            }
                        }
                    }
                }
            }
        }
        return work;
    }
    
    private void findJavaReferences(IProgressMonitor monitor) {
        Declaration declaration = 
                (Declaration) referencedDeclaration;
        SearchPattern searchPattern = 
                createSearchPattern(declaration, limitTo());
        if (searchPattern==null) {
            return;
        }
        runSearch(monitor, 
                new SearchEngine(), 
                searchPattern, 
                getProjectsToSearch(project), 
                new NewSearchResultCollector(result, true) {
            @Override
            public void acceptSearchMatch(SearchMatch match)
                    throws CoreException {
                IJavaElement enclosingElement = 
                        (IJavaElement) 
                            match.getElement();
                if (!filters.isFiltered(enclosingElement)) {
                    IJavaModelAware unit = 
                            getUnit(enclosingElement);
                    if (unit == null) {
                        return;
                    }
                    if (unit instanceof CeylonBinaryUnit) {
                        CeylonBinaryUnit binaryUnit = 
                                (CeylonBinaryUnit) unit;
                        if (binaryUnit.getCeylonSourceRelativePath()!=null) {
                            // it's a class file built from Ceylon : 
                            //   we should find it from the Ceylon source archives.
                            return;
                        }
                    }
                    IResource resource = match.getResource();
                    IFolder exploded;
                    if (resource==null) {
                        exploded = null;
                    }
                    else {
                        exploded =
                                getCeylonClassesOutputFolder(
                                        resource.getProject());
                    }
                    if (exploded==null || 
                            !exploded.contains(resource)) {
                        super.acceptSearchMatch(match);
                        if (enclosingElement!=null && 
                                match.getAccuracy()
                                    !=SearchMatch.A_INACCURATE) {
                            count++;
                        }
                    }
                }
            }
        });
    }

    abstract int limitTo();
    
    private void findInUnits(
            Iterable<? extends PhasedUnit> units, 
            IProgressMonitor monitor) {
        for (PhasedUnit pu: units) {
            if (filters.isFiltered(pu.getPackage())) {
                continue;
            }
            VirtualFile unitFile = pu.getUnitFile();
            monitor.subTask("Searching source file " + 
                    unitFile.getPath());
            Tree.CompilationUnit rootNode = getRootNode(pu);
            Set<Node> nodes = 
                    getNodes(rootNode, 
                            referencedDeclaration);
            //TODO: should really add these as we find them:
            for (Node node: nodes) {
                if (node.getToken()==null) {
                    //a synthetic node inserted in the tree
                }
                else {
                    CeylonSearchMatch match = 
                            CeylonSearchMatch.create(node, 
                                    rootNode, unitFile);
                    if (node instanceof Tree.DocLink) {
                        Tree.DocLink link = 
                                (Tree.DocLink) node;
                        if (link.getBase()
                                .equals(referencedDeclaration)) {
                            IRegion r = nameRegion(link, 0);
                            match.setOffset(r.getOffset());
                            match.setLength(r.getLength());
                        }
                        else {
                            for (Declaration d: link.getQualified()) {
                                if (d.equals(referencedDeclaration)) {
                                    IRegion r = nameRegion(link, 0);
                                    match.setOffset(r.getOffset());
                                    match.setLength(r.getLength());
                                }
                            }
                        }
                    }
                    result.addMatch(match);
                    count++;
                }
            }
            if (monitor.isCanceled()) {
                throw new OperationCanceledException();
            }
        }
    }

    Tree.CompilationUnit getRootNode(PhasedUnit pu) {
        for (IEditorPart editor: page.getDirtyEditors()) {
            if (editor instanceof CeylonEditor) {
                CeylonEditor ce = (CeylonEditor) editor;
                CeylonParseController cpc = 
                        ce.getParseController();
                Unit editorUnit = 
                        cpc.getLastCompilationUnit()
                            .getUnit();
                if (/*editor.isDirty() &&*/
                    pu.getUnit().equals(editorUnit)) {
                    return cpc.getLastCompilationUnit();
                }
            }
        }
        return pu.getCompilationUnit();
    }
    
    protected abstract Set<Node> getNodes(
            Tree.CompilationUnit cu, 
            Referenceable referencedDeclaration);
    
    protected abstract String labelString();

    @Override
    public ISearchResult getSearchResult() {
        return result;
    }
    
    @Override
    public String getLabel() {
        return "Displaying " + 
                count + " " + 
                labelString() + 
                " '" + name + "'";
    }
    
    @Override
    public boolean canRunInBackground() {
        return true;
    }
    
    @Override
    public boolean canRerun() {
        return false;
    }
}