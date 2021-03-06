import com.redhat.ceylon.compiler.typechecker.context {
    PhasedUnit
}
import com.redhat.ceylon.compiler.typechecker.tree {
    Node,
    Tree
}
import com.redhat.ceylon.eclipse.code.editor {
    CeylonEditor
}
import com.redhat.ceylon.eclipse.core.builder {
    CeylonBuilder
}
import com.redhat.ceylon.eclipse.util {
    EditorUtil,
    Nodes
}
import com.redhat.ceylon.ide.common.refactoring {
    AbstractRefactoring
}
import com.redhat.ceylon.model.typechecker.model {
    Declaration
}

import java.util {
    List,
    ArrayList
}

import org.antlr.runtime {
    CommonToken
}
import org.eclipse.core.resources {
    IProject,
    IResource,
    IFolder,
    IFile
}
import org.eclipse.jface.text {
    IDocument
}
import org.eclipse.ltk.core.refactoring {
    DocumentChange,
    TextFileChange,
    TextChange

}
import org.eclipse.ui {
    IFileEditorInput,
    IEditorPart
}
import org.eclipse.ui.texteditor {
    ITextEditor
}
import com.redhat.ceylon.eclipse.core.vfs {
    IFileVirtualFile
}
import com.redhat.ceylon.ide.common.model {
    CrossProjectSourceFile,
    CrossProjectBinaryUnit,
    IResourceAware,
    EditedSourceFile,
    ProjectSourceFile
}
import com.redhat.ceylon.ide.common.typechecker {
    ProjectPhasedUnit
}

abstract class EclipseAbstractRefactoring<RefactoringData>(IEditorPart editorPart)
        extends Refactoring()
        satisfies AbstractRefactoring<RefactoringData> {

    shared class EclipseEditorData() satisfies EditorData {
        assert (is CeylonEditor ce=editorPart);
        assert(is ITextEditor editorPart);
        shared CeylonEditor editor=ce;
        shared IDocument? document = editor.documentProvider.getDocument(editorPart.editorInput);
        shared IProject? project = EditorUtil.getProject(editorPart);
        shared actual List<CommonToken>? tokens = editor.parseController.tokens;
        shared actual Tree.CompilationUnit? rootNode = editor.parseController.typecheckedRootNode;
        shared actual Node? node;
        shared actual IFileVirtualFile? sourceVirtualFile;
        if (exists existingRootNode=rootNode,
            is IFileEditorInput input = editorPart.editorInput) {
            sourceVirtualFile = if (exists file=EditorUtil.getFile(input)) then IFileVirtualFile(file) else null;
            node = Nodes.findNode(rootNode, tokens, EditorUtil.getSelection(editorPart));
        } else {
            sourceVirtualFile = null;
            node = null;
        }
    }

    shared actual EclipseEditorData? editorData;
    if (is CeylonEditor ce=editorPart) {
        editorData = EclipseEditorData();
    } else {
        editorData = null;
    }

    shared actual Tree.CompilationUnit? rootNode => editorData?.rootNode;

    shared Boolean inSameProject(Declaration declaration) {
        value unit = declaration.unit;
        if (unit is CrossProjectSourceFile<IProject,IResource,IFolder,IFile> || 
            unit is CrossProjectBinaryUnit<IProject,IResource,IFolder,IFile, out Anything, out Anything>) {
            return false;
        }
        if (is IResourceAware<out Anything, out Anything, out Anything> unit) {
            if (exists p = unit.resourceProject,
                exists editorProject=editorData?.project) {
                return p.equals(editorProject);
            }
        }
        return false;
    }

    shared Boolean isEditable()
            => rootNode?.unit is EditedSourceFile<IProject,IResource,IFolder,IFile> ||
            rootNode?.unit is ProjectSourceFile<IProject,IResource,IFolder,IFile>;

    shared actual String toString(Node term) {
        assert(editorData exists);
        return Nodes.text(term, editorData?.tokens);
    }

    shared DocumentChange? newDocumentChange() {
        assert(editorData exists);
        value dc = DocumentChange(editorPart.editorInput.name + " \{#2014} current editor", editorData?.document);
        dc.textType = "ceylon";
        return dc;
    }

    shared TextFileChange newTextFileChange(PhasedUnit pu) {
        assert(is ProjectPhasedUnit<IProject,IResource,IFolder,IFile> pu);
        TextFileChange tfc = TextFileChange(name, pu.resourceFile);
        tfc.textType = "ceylon";
        return tfc;
    }

    shared actual Boolean searchInEditor()
            => if (exists ceylonEditor=editorData?.editor)
            then ceylonEditor.dirty
            else false;

    shared actual Boolean searchInFile(PhasedUnit pu)
            => if (exists ceylonEditor=editorData?.editor,
                    exists typecheckedRootNode=rootNode)
            then !ceylonEditor.dirty
            || pu.unit != typecheckedRootNode.unit
            else true;

    shared TextChange newLocalChange() {
        assert(exists editorData);
        TextChange tc;
        if (searchInEditor()) {
            assert(exists doc = editorData.document);
            tc = DocumentChange(name, editorData.document);
        } else {
            assert(exists file = editorData.sourceVirtualFile?.nativeResource);
            tc = TextFileChange(name, file);
        }
        tc.textType = "ceylon";
        return tc;
    }

    shared actual List<PhasedUnit> getAllUnits() {
        assert(editorData exists);
        assert(exists project = editorData?.project);
        List<PhasedUnit> units = ArrayList<PhasedUnit>();
        units.addAll(CeylonBuilder.getUnits(project));
        for (p in project.referencingProjects.iterable) {
            units.addAll(CeylonBuilder.getUnits(p));
        }

        return units;
    }
}