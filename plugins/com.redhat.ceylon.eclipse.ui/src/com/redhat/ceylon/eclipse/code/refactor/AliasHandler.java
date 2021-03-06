package com.redhat.ceylon.eclipse.code.refactor;

import static com.redhat.ceylon.eclipse.code.refactor.RenameLinkedMode.useLinkedMode;
import static com.redhat.ceylon.eclipse.util.EditorUtil.getCurrentEditor;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.ui.texteditor.ITextEditor;

import com.redhat.ceylon.eclipse.code.editor.CeylonEditor;

public class AliasHandler extends AbstractHandler {
        
    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        ITextEditor editor = (ITextEditor) getCurrentEditor();
        if (useLinkedMode() && editor instanceof CeylonEditor) {
            CeylonEditor ce = (CeylonEditor)editor;
            if (ce.isInLinkedMode()) {
                if (ce.getLinkedModeOwner() instanceof AliasLinkedMode) {
                    AliasLinkedMode current = (AliasLinkedMode) ce.getLinkedModeOwner();
                    current.enterDialogMode();
                    current.openDialog();
                }
                else {
                    new AliasRefactoringAction(editor).run();
                }
            }
            else {
                new AliasLinkedMode(ce).start();
            }
        }
        else {
            new AliasRefactoringAction(editor).run();
        }
        return null;
    }
            
}
