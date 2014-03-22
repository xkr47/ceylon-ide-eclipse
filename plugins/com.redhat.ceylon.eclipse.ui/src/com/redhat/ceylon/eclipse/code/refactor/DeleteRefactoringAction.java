package com.redhat.ceylon.eclipse.code.refactor;

import static com.redhat.ceylon.eclipse.ui.CeylonPlugin.PLUGIN_ID;

import org.eclipse.ltk.ui.refactoring.RefactoringWizard;
import org.eclipse.ui.IEditorPart;

public class DeleteRefactoringAction extends AbstractRefactoringAction {
    public DeleteRefactoringAction(IEditorPart editor) {
        super("Delete.", editor);
        setActionDefinitionId(PLUGIN_ID + ".action.delete");
    }
    
    @Override
    public Refactoring createRefactoring() {
        return new DeleteRefactoring(getTextEditor());
    }
    
    @Override
    public RefactoringWizard createWizard(Refactoring refactoring) {
        return new DeleteWizard((AbstractRefactoring) refactoring);
    }
    
    @Override
    public String message() {
        return "No declaration name selected";
    }
    
}
