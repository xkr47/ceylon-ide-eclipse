package com.redhat.ceylon.eclipse.code.correct;

import static com.redhat.ceylon.eclipse.ui.CeylonResources.COMPOSITE_CHANGE;
import static java.util.Collections.emptyList;

import java.util.Collection;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.TextChange;
import org.eclipse.text.edits.DeleteEdit;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.ui.IEditorPart;

import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.CompilationUnit;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.Identifier;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.MethodDeclaration;
import com.redhat.ceylon.eclipse.code.editor.CeylonEditor;
import com.redhat.ceylon.eclipse.code.refactor.RenameRefactoring;
import com.redhat.ceylon.model.typechecker.model.Function;

class ConvertFunctionToGetterProposal extends CorrectionProposal {

    private ConvertToGetterRefactoring refactoring;

    private static final class ConvertToGetterRefactoring extends RenameRefactoring {
        private ConvertToGetterRefactoring(IEditorPart editor) {
            super(editor);
        }

        @Override
        public String getName() {
            return "Convert to Getter";
        }

        @Override
        public RefactoringStatus checkFinalConditions(IProgressMonitor pm)
                throws CoreException, OperationCanceledException {
            return new RefactoringStatus();
        }

        @Override
        protected void refactorJavaReferences(IProgressMonitor pm, CompositeChange cc) {
            // TODO!
        }
        
        @Override
        protected void renameIdentifier(TextChange tfc, Identifier id, CompilationUnit root) {}

        @Override
        protected void renameRegion(TextChange tfc, Region region, CompilationUnit root) {}

        @Override
        public List<Identifier> getIdentifiersToRename(CompilationUnit root) {
            return emptyList();
        }

        @Override
        public List<Region> getStringsToReplace(CompilationUnit root) {
            return emptyList();
        }

        @Override
        protected void renameNode(TextChange tfc, Node node, Tree.CompilationUnit root) {
            Integer startIndex = null;
            Integer endIndex = null;
            
            if (node instanceof Tree.AnyMethod) {
                Tree.AnyMethod am = (Tree.AnyMethod) node;
                Tree.Type type = am.getType();
                if (type instanceof Tree.FunctionModifier) {
                    tfc.setEdit(new ReplaceEdit(
                            type.getStartIndex(), 
                            type.getDistance(), 
                            "value"));
                }
                Tree.ParameterList parameterList = 
                        am.getParameterLists().get(0);
                startIndex = parameterList.getStartIndex();
                endIndex = parameterList.getEndIndex();
            }
            else {
                FindInvocationVisitor fiv = 
                        new FindInvocationVisitor(node);
                fiv.visit(root);
                if (fiv.result != null && 
                        fiv.result.getPrimary() == node) {
                    Tree.PositionalArgumentList pal = 
                            fiv.result.getPositionalArgumentList();
                    startIndex = pal.getStartIndex();
                    endIndex = pal.getEndIndex();
                }
            }
            
            if (startIndex != null && endIndex != null) {
                tfc.addEdit(new DeleteEdit(startIndex, endIndex - startIndex));
            }
        }
    }

    static void addConvertFunctionToGetterProposal(
            Collection<ICompletionProposal> proposals, 
            CeylonEditor editor, Node node) {
        
        Function method;
        if (node instanceof Tree.MethodDefinition) {
            Tree.MethodDefinition md = 
                    (Tree.MethodDefinition) node;
            method = md.getDeclarationModel();
        }
        else if (node instanceof Tree.MethodDeclaration) {
            MethodDeclaration md = 
                    (Tree.MethodDeclaration) node;
            if (md.getSpecifierExpression() 
                    instanceof Tree.LazySpecifierExpression) {
                method = md.getDeclarationModel();
            }
            else {
                return;
            }
        }
        else {
            return;
        }

        if (method!=null 
                && !method.isDeclaredVoid()
                && method.getParameterLists().size()==1 
                && method.getParameterLists().get(0).getParameters().isEmpty()) {
            addConvertFunctionToGetterProposal(proposals, editor, method);
        }
    }

    private static void addConvertFunctionToGetterProposal(
            Collection<ICompletionProposal> proposals, 
            CeylonEditor editor, Function method) {
        ConvertToGetterRefactoring refactoring = 
                new ConvertToGetterRefactoring(editor);
        try {            
            if (refactoring.getDeclaration() == null 
                    || !refactoring.getDeclaration().equals(method) 
                    || !refactoring.getEnabled()
                    || !refactoring.checkAllConditions(new NullProgressMonitor()).isOK()) {
                return;
            }
        }
        catch (OperationCanceledException e) {
            // noop
            return;
        }
        catch (CoreException e) {
            e.printStackTrace();
            return;
        }

        String desc = 
                "Convert " +
                (method.isToplevel() ? "function" : "method") +
                " '" + method.getName() + "()' to getter";
        ConvertFunctionToGetterProposal proposal = 
                new ConvertFunctionToGetterProposal(
                        desc, method, refactoring);
        if (!proposals.contains(proposal)) {
            proposals.add(proposal);
        }
    }
    
    @Override
    protected Change createChange() throws CoreException {
        CompositeChange change = 
                refactoring.createChange(
                        new NullProgressMonitor());
//        if (change.getChildren().length == 0) {
//            return;
//        }        
        return change;
    }

    private ConvertFunctionToGetterProposal(
            String desc, Function method, 
            ConvertToGetterRefactoring refactoring) {
        super(desc, null, null, COMPOSITE_CHANGE);
        this.refactoring = refactoring;
    }

}