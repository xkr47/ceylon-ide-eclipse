package com.redhat.ceylon.eclipse.code.correct;

import static com.redhat.ceylon.eclipse.code.correct.AddAnnotionProposal.createInsertAnnotationEdit;
import static com.redhat.ceylon.eclipse.code.correct.AddAnnotionProposal.getAnnotationIdentifier;

import java.util.Collection;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.eclipse.text.edits.InsertEdit;

import com.redhat.ceylon.model.typechecker.model.Declaration;
import com.redhat.ceylon.model.typechecker.model.Type;
import com.redhat.ceylon.model.typechecker.model.TypeDeclaration;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.ide.common.util.FindContainerVisitor;

public class AddThrowsAnnotationProposal extends CorrectionProposal {
    
    public static void addThrowsAnnotationProposal(Collection<ICompletionProposal> proposals, 
            Tree.Statement statement, Tree.CompilationUnit cu, IFile file, IDocument doc) {
        Type exceptionType = determineExceptionType(statement);
        if (exceptionType == null) {
            return;
        }
        
        Tree.Declaration throwContainer = determineThrowContainer(statement, cu);
        if( !(throwContainer instanceof Tree.MethodDefinition) && 
            !(throwContainer instanceof Tree.AttributeGetterDefinition) && 
            !(throwContainer instanceof Tree.AttributeSetterDefinition) && 
            !(throwContainer instanceof Tree.ClassOrInterface) ) {
            return;               
        }
        
        if (isAlreadyPresent(throwContainer, exceptionType)) {
            return;
        }

        String throwsAnnotation = "throws (`class " + exceptionType.asString() + "`, \"\")";
        InsertEdit throwsAnnotationInsertEdit = createInsertAnnotationEdit(throwsAnnotation, throwContainer, doc);
        TextFileChange throwsAnnotationChange = new TextFileChange("Add Throws Annotation", file);
        throwsAnnotationChange.setEdit(throwsAnnotationInsertEdit);

        int cursorOffset = throwsAnnotationInsertEdit.getOffset() + throwsAnnotationInsertEdit.getText().indexOf(")") - 1;
        AddThrowsAnnotationProposal proposal = 
                new AddThrowsAnnotationProposal(throwsAnnotationChange, exceptionType, cursorOffset, 
                        throwContainer.getIdentifier() != null ? throwContainer.getIdentifier().getText() : "");
        if (!proposals.contains(proposal)) {
            proposals.add(proposal);
        }
    }

    private static Type determineExceptionType(Tree.Statement statement) {
        Type exceptionType = null;
    
        if (statement instanceof Tree.Throw) {
            Type ceylonLangExceptionType = statement.getUnit().getExceptionDeclaration().getType();
            Tree.Expression throwExpression = ((Tree.Throw) statement).getExpression();
            if (throwExpression == null) {
                exceptionType = ceylonLangExceptionType;
            } else {
                Type throwExpressionType = throwExpression.getTypeModel();
                if ( throwExpressionType != null && 
                     throwExpressionType.isSubtypeOf(ceylonLangExceptionType) ) {
                    exceptionType = throwExpressionType;
                }
            }
        }
    
        return exceptionType;
    }

    private static Tree.Declaration determineThrowContainer(Tree.Statement statement, 
            Tree.CompilationUnit cu) {
        FindContainerVisitor fcv = new FindContainerVisitor(statement);
        fcv.visit(cu);
        return fcv.getDeclaration();
    }

    private static boolean isAlreadyPresent(Tree.Declaration throwContainer, Type exceptionType) {
        Tree.AnnotationList annotationList = throwContainer.getAnnotationList();
        if (annotationList != null) {
            for (Tree.Annotation annotation : annotationList.getAnnotations()) {
                String annotationIdentifier = getAnnotationIdentifier(annotation);
                if ("throws".equals(annotationIdentifier)) {
                    Tree.PositionalArgumentList positionalArgumentList = annotation.getPositionalArgumentList();
                    if (positionalArgumentList != null && 
                            positionalArgumentList.getPositionalArguments() != null &&
                            positionalArgumentList.getPositionalArguments().size() > 0) {
                        Tree.PositionalArgument throwsArg = positionalArgumentList.getPositionalArguments().get(0);
                        if (throwsArg instanceof Tree.ListedArgument) {
                            Tree.Expression throwsArgExp = ((Tree.ListedArgument) throwsArg).getExpression();
                            if (throwsArgExp != null) {
                                Tree.Term term = throwsArgExp.getTerm();
                                if (term instanceof Tree.MemberOrTypeExpression) {
                                    Declaration declaration = ((Tree.MemberOrTypeExpression) term).getDeclaration();
                                    if (declaration instanceof TypeDeclaration) {
                                        Type type = ((TypeDeclaration) declaration).getType();
                                        if (exceptionType.isExactly(type)) {
                                            return true;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    private AddThrowsAnnotationProposal(Change change, Type exceptionType, int offset, String declName) {
        super("Add throws annotation to '" + declName + "'", change, new Region(offset, 0));
    }
    

}