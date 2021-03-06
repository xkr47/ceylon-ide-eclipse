import org.eclipse.jface.text {
    IDocument
}
import org.eclipse.text.edits {
    TextEdit,
    InsertEdit,
    DeleteEdit,
    ReplaceEdit,
    MultiTextEdit
}
import org.eclipse.ltk.core.refactoring {
    TextChange
}
import com.redhat.ceylon.ide.common.correct {
    DocumentChanges
}
import com.redhat.ceylon.eclipse.util {
    EditorUtil
}

shared interface EclipseDocumentChanges
        satisfies DocumentChanges<IDocument, InsertEdit, TextEdit, TextChange> {

    shared actual void initMultiEditChange(TextChange importChange) {
        importChange.edit = MultiTextEdit();
    }

    shared actual IDocument getDocumentForChange(TextChange change)
            => EditorUtil.getDocument(change);

    shared actual TextEdit newDeleteEdit(Integer start, Integer length)
            => DeleteEdit(start, length);

    shared actual TextEdit newReplaceEdit(Integer start, Integer length, String text)
            => ReplaceEdit(start, length, text);

    shared actual InsertEdit newInsertEdit(Integer position, String text)
            => InsertEdit(position, text);

    shared actual void addEditToChange(TextChange change, TextEdit edit) {
        if (is MultiTextEdit me = change.edit) {
            change.addEdit(edit);
        } else {
            change.edit = edit;            
        }
    }

    shared actual String getInsertedText(InsertEdit edit)
            => edit.text;
    
    shared actual Boolean hasChildren(TextChange change)
            => change.edit.hasChildren();
}