package com.redhat.ceylon.eclipse.code.refactor;

import static com.redhat.ceylon.eclipse.util.Indents.indents;
import static com.redhat.ceylon.eclipse.util.Nodes.findToplevelStatement;
import static com.redhat.ceylon.eclipse.util.Nodes.text;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.antlr.runtime.CommonToken;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jface.text.IRegion;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.DocumentChange;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.TextChange;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.eclipse.text.edits.DeleteEdit;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.ui.IEditorPart;

import com.redhat.ceylon.compiler.typechecker.context.PhasedUnit;
import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.TreeUtil;
import com.redhat.ceylon.compiler.typechecker.tree.Visitor;
import com.redhat.ceylon.eclipse.code.parse.CeylonParseController;
import com.redhat.ceylon.eclipse.util.FindRefinementsVisitor;
import com.redhat.ceylon.ide.common.typechecker.ProjectPhasedUnit;
import com.redhat.ceylon.ide.common.util.escaping_;
import com.redhat.ceylon.model.typechecker.model.Declaration;
import com.redhat.ceylon.model.typechecker.model.FunctionOrValue;
import com.redhat.ceylon.model.typechecker.model.Parameter;

public class CollectParametersRefactoring extends AbstractRefactoring {
    
    private Declaration declaration;
    private int parameterListIndex;
    private List<Tree.Parameter> parameters = 
            new ArrayList<Tree.Parameter>();
    private Set<FunctionOrValue> models = 
            new HashSet<FunctionOrValue>();
    private int firstParam=-1;
    private int lastParam;
    
    private class FindParametersVisitor extends Visitor {
        private void handleParamList(Tree.Declaration that, int i,
                Tree.ParameterList pl) {
            if (pl==null) return;
            IRegion selection = editor.getSelection();
            int start = selection.getOffset();
            int end = selection.getOffset() + selection.getLength();
            if (start>pl.getStartIndex() && start<pl.getEndIndex()) {
                parameterListIndex = i;
                declaration = that.getDeclarationModel();
                for (int j=0; 
                        j<pl.getParameters().size(); 
                        j++) {
                    Tree.Parameter p = pl.getParameters().get(j);
                    if (p.getStartIndex()>=start && p.getEndIndex()<=end) {
                        parameters.add(p);
                        models.add(p.getParameterModel().getModel());
                        if (firstParam==-1) firstParam=j;
                        lastParam=j;
                    }
                }
            }
        }
        @Override
        public void visit(Tree.AnyMethod that) {
            for (int i=0; 
                    i<that.getParameterLists().size(); 
                    i++) {
                handleParamList(that, i, 
                        that.getParameterLists().get(i));
            }
            super.visit(that);
        }
        @Override
        public void visit(Tree.ClassDefinition that) {
            handleParamList(that, 0, 
                    that.getParameterList());
            super.visit(that);
        }        
    }
    
    private static class FindInvocationsVisitor extends Visitor {
        private Declaration declaration;
        private final Set<Tree.ArgumentList> results = 
                new HashSet<Tree.ArgumentList>();
        Set<Tree.ArgumentList> getResults() {
            return results;
        }
        private FindInvocationsVisitor(Declaration declaration) {
            this.declaration=declaration;
        }
        @Override
        public void visit(Tree.InvocationExpression that) {
            super.visit(that);
            Tree.Primary primary = that.getPrimary();
            if (primary instanceof Tree.MemberOrTypeExpression) {
                Tree.MemberOrTypeExpression mte = 
                        (Tree.MemberOrTypeExpression) 
                            primary;
                if (mte.getDeclaration()
                        .equals(declaration)) {
                    Tree.PositionalArgumentList pal = 
                            that.getPositionalArgumentList();
                    if (pal!=null) {
                        results.add(pal);
                    }
                    Tree.NamedArgumentList nal = 
                            that.getNamedArgumentList();
                    if (nal!=null) {
                        results.add(nal);
                    }
                }
            }
        }
    }
    
    private static class FindArgumentsVisitor extends Visitor {
        private Declaration declaration;
        private final Set<Tree.MethodArgument> results = 
                new HashSet<Tree.MethodArgument>();
        Set<Tree.MethodArgument> getResults() {
            return results;
        }
        private FindArgumentsVisitor(Declaration declaration) {
            this.declaration=declaration;
        }
        @Override
        public void visit(Tree.MethodArgument that) {
            super.visit(that);
            Parameter p = that.getParameter();
            if (p!=null && p.getModel().equals(declaration)) {
                results.add(that);
            }
        }
    }

    private String newName;
        
    public String getNewName() {
        return newName;
    }
    
    public void setNewName(String newName) {
        this.newName = newName;
    }
    
    public CollectParametersRefactoring(IEditorPart editor) {
        super(editor);
        if (rootNode!=null) {
            new FindParametersVisitor().visit(rootNode);
            if (declaration!=null) {
                newName = 
                        escaping_.get_()
                            .toInitialUppercase(
                                declaration.getName());
            }
        }
    }
    
    @Override
    public boolean getEnabled() {
        return declaration!=null && 
                !parameters.isEmpty();
    }
    
    @Override
    int countReferences(Tree.CompilationUnit cu) {
        FindInvocationsVisitor frv = 
                new FindInvocationsVisitor(declaration);
        FindRefinementsVisitor fdv = 
                new FindRefinementsVisitor(declaration);
        FindArgumentsVisitor fav = 
                new FindArgumentsVisitor(declaration);
        cu.visit(frv);
        cu.visit(fdv);
        cu.visit(fav);
        return frv.getResults().size() + 
                fdv.getDeclarationNodes().size() + 
                fav.getResults().size();
    }

    public String getName() {
        return "Collect Parameters";
    }

    public RefactoringStatus checkInitialConditions(IProgressMonitor pm)
            throws CoreException, 
                   OperationCanceledException {
        // Check parameters retrieved from editor context
        return new RefactoringStatus();
    }

    public RefactoringStatus checkFinalConditions(IProgressMonitor pm)
            throws CoreException, 
                   OperationCanceledException {
        return new RefactoringStatus();
    }

    public CompositeChange createChange(IProgressMonitor pm) 
            throws CoreException,
                   OperationCanceledException {
        List<PhasedUnit> units = getAllUnits();
        pm.beginTask(getName(), units.size());
        CompositeChange cc = new CompositeChange(getName());
        int i=0;
        for (PhasedUnit pu: units) {
            if (searchInFile(pu)) {
                ProjectPhasedUnit<IProject,IResource,IFolder,IFile> ppu = 
                        (ProjectPhasedUnit<IProject,IResource,IFolder,IFile>) pu;
                TextFileChange tfc = 
                        newTextFileChange(ppu);
                refactorInFile(tfc, cc, 
                        pu.getCompilationUnit(), 
                        pu.getTokens());
                pm.worked(i++);
            }
        }
        if (searchInEditor()) {
            DocumentChange dc = newDocumentChange();
            CeylonParseController pc = 
                    editor.getParseController();
            refactorInFile(dc, cc, 
                    pc.getLastCompilationUnit(),
                    pc.getTokens());
            pm.worked(i++);
        }
        pm.done();
        return cc;
    }

    private void refactorInFile(final TextChange tfc, 
            CompositeChange cc, Tree.CompilationUnit root, 
            List<CommonToken> toks) {
        tfc.setEdit(new MultiTextEdit());
        if (declaration!=null) {
            String paramName = 
                    escaping_.get_()
                        .toInitialLowercase(newName);
            FindInvocationsVisitor fiv = 
                    new FindInvocationsVisitor(declaration);
            root.visit(fiv);
            for (Tree.ArgumentList pal: fiv.getResults()) {
                refactorInvocation(tfc, paramName, pal, toks);
            }
            FindRefinementsVisitor frv = 
                    new FindRefinementsVisitor(declaration);
            root.visit(frv);
            for (Tree.StatementOrArgument decNode: 
                    frv.getDeclarationNodes()) {
                refactorDeclaration(tfc, paramName, decNode);
            }
            FindArgumentsVisitor fav = 
                    new FindArgumentsVisitor(declaration);
            root.visit(fav);
            for (Tree.MethodArgument decNode: fav.getResults()) {
                refactorArgument(tfc, paramName, decNode);
            }
            createNewClassDeclaration(tfc, root);
        }
        if (tfc.getEdit().hasChildren()) {
            cc.add(tfc);
        }
    }

    private void refactorInvocation(TextChange tfc,
            String paramName, Tree.ArgumentList al, 
            List<CommonToken> toks) {
        if (al instanceof Tree.PositionalArgumentList) {
            Tree.PositionalArgumentList pal = 
                    (Tree.PositionalArgumentList) al;
            List<Tree.PositionalArgument> pas = 
                    pal.getPositionalArguments();
            if (pas.size()>firstParam) {
                Integer startIndex = 
                        pas.get(firstParam)
                            .getStartIndex();
                tfc.addEdit(new InsertEdit(startIndex, newName + "("));
                Integer stopIndex = 
                        pas.size()>lastParam && 
                            !pas.get(lastParam)
                                .getParameter()
                                .isSequenced() ?
                        pas.get(lastParam).getEndIndex():
                        pas.get(pas.size()-1).getEndIndex();
                tfc.addEdit(new InsertEdit(stopIndex, ")"));
            }
        }
        else if (al instanceof Tree.NamedArgumentList) {
            Tree.NamedArgumentList nal = 
                    (Tree.NamedArgumentList) al;
            List<Tree.NamedArgument> nas = 
                    nal.getNamedArguments();
            List<Tree.StatementOrArgument> results = 
                    new ArrayList<Tree.StatementOrArgument>();
            Tree.NamedArgument prev = null;
            for (Tree.NamedArgument na: nas) {
                FunctionOrValue p = 
                        na.getParameter().getModel();
                if (models.contains(p)) {
                    int fromOffset = results.isEmpty() ? 
                            na.getStartIndex() : 
                            prev.getEndIndex();
                    int toOffset = na.getEndIndex();
                    tfc.addEdit(new DeleteEdit(fromOffset, 
                            toOffset-fromOffset));
                    results.add(na);
                }
                prev = na;
            }
            Tree.SequencedArgument sa = 
                    nal.getSequencedArgument();
            if (sa!=null) {
                FunctionOrValue p = 
                        sa.getParameter().getModel();
                if (models.contains(p)) {
                    int fromOffset = sa.getStartIndex();
                    int toOffset = sa.getEndIndex();
                    tfc.addEdit(new DeleteEdit(fromOffset, 
                            toOffset-fromOffset));
                    results.add(sa);
                }
            }
            if (!results.isEmpty()) {
                StringBuilder builder = new StringBuilder();
                builder.append(paramName).append(" = ")
                       .append(newName).append(" { ");
                for (Tree.StatementOrArgument na: results) {
                    builder.append(text(na, toks)).append(" ");
                }
                builder.append("};");
                tfc.addEdit(new InsertEdit(
                        results.get(0).getStartIndex(), 
                        builder.toString()));
            }
        }
    }

    private void createNewClassDeclaration(final TextChange tfc,
            Tree.CompilationUnit root) {
        if (declaration.getUnit().equals(root.getUnit())) {
            String delim = indents().getDefaultLineDelimiter(document);
            //TODO: for unshared declarations, we don't 
            //      need to make it toplevel, I guess
            int loc = findToplevelStatement(rootNode, node).getStartIndex();
            StringBuilder builder = new StringBuilder();
            if (declaration.isShared()) {
                builder.append("shared ");
            }
            builder.append("class ").append(newName).append("(");
            for (Tree.Parameter p: parameters) {
                boolean addShared = true;
                if (p instanceof Tree.ParameterDeclaration) {
                    Tree.ParameterDeclaration pd = 
                            (Tree.ParameterDeclaration) p;
                    Tree.TypedDeclaration ptd = 
                            pd.getTypedDeclaration();
                    if (TreeUtil.hasAnnotation(ptd.getAnnotationList(), 
                            "shared", ptd.getUnit())) {
                        addShared = false;
                    }
                }
                if (addShared) {
                    builder.append("shared ");
                }
                builder.append(text(p, tokens)).append(", ");
            }
            if (builder.toString().endsWith(", ")) {
                builder.setLength(builder.length()-2);
            }
            builder.append(") {}").append(delim).append(delim);
            tfc.addEdit(new InsertEdit(loc, builder.toString()));
        }
    }

    private void refactorArgument(TextChange tfc, 
            String paramName, Tree.MethodArgument decNode) {
        refactorDec(tfc, paramName, 
                decNode.getParameterLists()
                    .get(parameterListIndex), 
                decNode.getBlock());
    }

    private void refactorDeclaration(TextChange tfc, 
            String paramName, Tree.StatementOrArgument decNode) {
        Tree.ParameterList pl;
        Node body;
        if (decNode instanceof Tree.MethodDefinition) {
            Tree.MethodDefinition md = 
                    (Tree.MethodDefinition) 
                        decNode;
            pl = md.getParameterLists()
                    .get(parameterListIndex);
            body = md.getBlock();
        }
        else if (decNode instanceof Tree.MethodDeclaration) {
            Tree.MethodDeclaration md = 
                    (Tree.MethodDeclaration) 
                        decNode;
            pl = md.getParameterLists()
                    .get(parameterListIndex);
            body = md.getSpecifierExpression();
        }
        else if (decNode instanceof Tree.ClassDefinition) {
            Tree.ClassDefinition cd = 
                    (Tree.ClassDefinition) decNode;
            pl = cd.getParameterList();
            body = cd.getClassBody();
        }
        else if (decNode instanceof Tree.SpecifierStatement) {
            Tree.SpecifierStatement ss = 
                    (Tree.SpecifierStatement) 
                        decNode;
            Tree.Term bme = ss.getBaseMemberExpression();
            body = ss.getSpecifierExpression();
            if (bme instanceof Tree.ParameterizedExpression) {
                Tree.ParameterizedExpression pe = 
                        (Tree.ParameterizedExpression) bme;
                pl = pe.getParameterLists().get(parameterListIndex);
            }
            else {
                return;
            }
        }
        else {
            return;
        }
        refactorDec(tfc, paramName, pl, body);
    }
    
    private void refactorDec(final TextChange tfc, 
            final String paramName,
            Tree.ParameterList pl, Node body) {
        List<Tree.Parameter> ps = pl.getParameters();
        final Set<FunctionOrValue> params = 
                new HashSet<FunctionOrValue>();
        boolean allDefaulted = true;
        for (int i=firstParam; 
                i<ps.size()&&i<=lastParam; 
                i++) {
            Parameter p = ps.get(i).getParameterModel();
            params.add(p.getModel());
            if (!p.isDefaulted()) {
                allDefaulted = false;
            }
        }
        int startOffset = ps.get(firstParam).getStartIndex();
        int endOffset = ps.get(lastParam).getEndIndex();
        String text = newName + " " + paramName;
        if (allDefaulted) {
            text += " = " + newName + "()";
        }
        tfc.addEdit(new InsertEdit(startOffset, text));
        tfc.addEdit(new DeleteEdit(startOffset, endOffset-startOffset));
        for (int i=lastParam+1; i<ps.size(); i++) {
            refactorLocalRefs(tfc, paramName, params, ps.get(i));
        }
        refactorLocalRefs(tfc, paramName, params, body);
    }

    private void refactorLocalRefs(final TextChange tfc,
            final String paramName, 
            final Set<FunctionOrValue> params, 
            Node node) {
        node.visit(new Visitor() {
            @Override
            public void visit(Tree.BaseMemberExpression that) {
                super.visit(that);
                if (params.contains(that.getDeclaration())) {
                    tfc.addEdit(new InsertEdit(that.getStartIndex(), 
                            paramName + "."));
                }
            }
        });
    }
    
}
