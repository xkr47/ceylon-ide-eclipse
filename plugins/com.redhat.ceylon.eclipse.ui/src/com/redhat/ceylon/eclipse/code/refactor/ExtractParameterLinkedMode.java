package com.redhat.ceylon.eclipse.code.refactor;

import static com.redhat.ceylon.eclipse.ui.CeylonPlugin.PLUGIN_ID;
import static com.redhat.ceylon.model.typechecker.model.ModelUtil.isTypeUnknown;

import org.eclipse.jface.text.IDocument;
import org.eclipse.ltk.core.refactoring.DocumentChange;
import org.eclipse.ltk.ui.refactoring.RefactoringWizard;
import org.eclipse.swt.widgets.Shell;

import com.redhat.ceylon.compiler.typechecker.tree.Tree.CompilationUnit;
import com.redhat.ceylon.eclipse.code.editor.CeylonEditor;
import com.redhat.ceylon.eclipse.util.EditorUtil;
import com.redhat.ceylon.model.typechecker.model.Type;

public final class ExtractParameterLinkedMode 
        extends ExtractLinkedMode {
        
    private final ExtractParameterRefactoring refactoring;
    
    public ExtractParameterLinkedMode(CeylonEditor editor) {
        super(editor);
        this.refactoring = new ExtractParameterRefactoring(editor);
    }
    
    @Override
    protected int performInitialChange(IDocument document) {
        DocumentChange change = 
                new DocumentChange("Extract Parameter", 
                        document);
        refactoring.extractInFile(change);
        EditorUtil.performChange(change);
        return 0;
    }
    
    @Override
    protected boolean canStart() {
        return refactoring.getEnabled();
    }
    
    @Override
    protected int getNameOffset() {
        return refactoring.getDecRegion().getOffset();
    }
    
    @Override
    protected int getTypeOffset() {
        return refactoring.getTypeRegion().getOffset();
    }
    
    @Override
    protected int getExitPosition(int selectionOffset, int adjust) {
        return refactoring.getRefRegion().getOffset();
    }
    
    @Override
    protected String[] getNameProposals() {
    	return refactoring.getNameProposals();
    }
    
    @Override
    protected void addLinkedPositions(IDocument document,
            CompilationUnit rootNode, int adjust) {
        
        addNamePosition(document, 
                refactoring.getRefRegion().getOffset(),
                refactoring.getRefRegion().getLength());
        
        Type type = refactoring.getType();
        if (!isTypeUnknown(type)) {
            addTypePosition(document, type, 
                    refactoring.getTypeRegion().getOffset(), 
                    refactoring.getTypeRegion().getLength());
        }
        
    }
    
    @Override
    protected String getName() {
        return refactoring.getNewName();
    }
    
    @Override
    protected void setName(String name) {
        refactoring.setNewName(name);
    }
    
    @Override
    protected boolean forceWizardMode() {
        return refactoring.forceWizardMode();
    }
    
    @Override
    protected String getActionName() {
        return PLUGIN_ID + ".action.extractParameter";
    }
    
    @Override
    protected void openPreview() {
        new RenameRefactoringAction(editor) {
            @Override
            public Refactoring createRefactoring() {
                return ExtractParameterLinkedMode.this.refactoring;
            }
            @Override
            public RefactoringWizard createWizard(Refactoring refactoring) {
                return new ExtractParameterWizard((ExtractParameterRefactoring) refactoring) {
                    @Override
                    protected void addUserInputPages() {}
                };
            }
        }.run();
    }

    @Override
    protected void openDialog() {
        new ExtractParameterRefactoringAction(editor) {
            @Override
            public Refactoring createRefactoring() {
                return ExtractParameterLinkedMode.this.refactoring;
            }
        }.run();
    }
    
    @Override
    protected String getKind() {
        return "parameter";
    }

    public static void selectExpressionAndStart(
            final CeylonEditor editor) {
        if (editor.getSelection().getLength()>0) {
            new ExtractParameterLinkedMode(editor).start();
        }
        else {
            Shell shell = editor.getSite().getShell();
            new SelectExpressionPopup(shell, 0, editor,
                    "Extract Parameter") {
                ExtractLinkedMode linkedMode() {
                    return new ExtractParameterLinkedMode(editor);
                }
            }
            .open();
        }
    }
    
}
