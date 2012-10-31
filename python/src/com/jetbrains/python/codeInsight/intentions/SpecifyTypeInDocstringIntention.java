package com.jetbrains.python.codeInsight.intentions;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.template.*;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.documentation.PyDocstringGenerator;
import com.jetbrains.python.documentation.PyDocumentationSettings;
import com.jetbrains.python.documentation.PythonDocumentationProvider;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyDynamicallyEvaluatedType;
import com.jetbrains.python.psi.types.PyReturnTypeReference;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

/**
 * User: ktisha
 *
 * Helps to specify type
 */
public class SpecifyTypeInDocstringIntention implements IntentionAction {
  private String myText = PyBundle.message("INTN.specify.type");
  public SpecifyTypeInDocstringIntention() {
  }

  @NotNull
  public String getText() {
    return myText;
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INTN.specify.type");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    myText = PyBundle.message("INTN.specify.type");
    PsiElement elementAt = PyUtil.findNonWhitespaceAtOffset(file, editor.getCaretModel().getOffset());
    if (elementAt == null) return false;
    PyCallExpression callExpression = PsiTreeUtil.getParentOfType(elementAt, PyCallExpression.class);
    if (callExpression != null && callExpression.resolveCalleeFunction(PyResolveContext.defaultContext()) != null) {
      PyAssignmentStatement assignmentStatement = PsiTreeUtil.getParentOfType(elementAt, PyAssignmentStatement.class);
      if (assignmentStatement != null) {
        final PyExpression assignedValue = assignmentStatement.getAssignedValue();
        if (assignedValue != null) {
          PyType type = assignedValue.getType(TypeEvalContext.slow());
          if (type == null || type instanceof PyReturnTypeReference) {
            myText = PyBundle.message("INTN.specify.return.type");
            return true;
          }
        }
      }
    }
    else {
      PyFunction parentFunction = PsiTreeUtil.getParentOfType(elementAt, PyFunction.class);
      if (parentFunction != null) {
        final ASTNode nameNode = parentFunction.getNameNode();
        if (nameNode != null && nameNode.getPsi() == elementAt) {
          myText = PyBundle.message("INTN.specify.return.type");
          return true;
        }
      }
    }

    PyExpression problemElement = PyUtil.findProblemElement(editor, file, PyNamedParameter.class, PyQualifiedExpression.class);

    if (problemElement == null) return false;
    if (problemElement instanceof PyQualifiedExpression) {
      final PyExpression qualifier = ((PyQualifiedExpression)problemElement).getQualifier();
      if (qualifier != null && !qualifier.getText().equals(PyNames.CANONICAL_SELF)) {
        problemElement = qualifier;
      }
    }
    if (problemElement.getParent() instanceof PyCallExpression
        || PsiTreeUtil.getParentOfType(problemElement, PyLambdaExpression.class) != null) {
      return false;
    }
    final PyType type = problemElement.getType(TypeEvalContext.slow());
    if (type == null || type instanceof PyReturnTypeReference || type instanceof PyDynamicallyEvaluatedType) {
      PyFunction pyFunction = PsiTreeUtil.getParentOfType(problemElement, PyFunction.class);
      PsiReference reference = problemElement.getReference();
      if (problemElement instanceof PyQualifiedExpression) {
        final PyExpression qualifier = ((PyQualifiedExpression)problemElement).getQualifier();
        if (qualifier != null && !qualifier.getText().equals(PyNames.CANONICAL_SELF)) reference = qualifier.getReference();
      }
      if (pyFunction != null && (problemElement instanceof PyParameter || reference != null && reference.resolve() instanceof PyParameter)) {
        final String docstring = pyFunction.getDocStringValue();
        if (docstring != null) {
          String name = problemElement.getName();
          if (problemElement instanceof PyQualifiedExpression) {
            final PyExpression qualifier = ((PyQualifiedExpression)problemElement).getQualifier();
            if (qualifier != null)
              name = qualifier.getText();
          }
          if (docstring.contains("type " + name + ":")) return false;
        }
        return true;
      }
    }
    return false;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiElement elementAt = file.findElementAt(editor.getCaretModel().getOffset() - 1);
    if (elementAt != null && !(elementAt.getNode().getElementType() == PyTokenTypes.IDENTIFIER)) {
      elementAt = file.findElementAt(editor.getCaretModel().getOffset());
    }

    String kind = "type";
    PyCallExpression callExpression = PyUtil.findProblemElement(editor, file, PyCallExpression.class);
    PyFunction pyFunction = PsiTreeUtil.getParentOfType(elementAt, PyFunction.class);
    PyExpression problemElement = PyUtil.findProblemElement(editor, file, PyNamedParameter.class, PyQualifiedExpression.class);
    if (callExpression != null) {
      PyAssignmentStatement assignmentStatement = PsiTreeUtil.getParentOfType(elementAt, PyAssignmentStatement.class);
      if (assignmentStatement != null) {
        PyType pyType = assignmentStatement.getAssignedValue().getType(TypeEvalContext.slow());
        if (pyType == null || pyType instanceof PyReturnTypeReference) {
          pyFunction = (PyFunction)callExpression.resolveCalleeFunction(PyResolveContext.defaultContext());
          problemElement = null;
          kind = "rtype";
        }
      }
    }
    if (pyFunction != null) {
      final ASTNode nameNode = pyFunction.getNameNode();
      if (nameNode != null && nameNode.getPsi() == elementAt) {
        kind = "rtype";
      }
    }

    PsiReference reference = null;

    PyDocstringGenerator docstringGenerator = new PyDocstringGenerator(pyFunction);


    String name = "";
    if (problemElement != null) {
      name = problemElement.getName();

      reference = problemElement.getReference();
      if (problemElement instanceof PyQualifiedExpression) {
        final PyExpression qualifier = ((PyQualifiedExpression)problemElement).getQualifier();
        if (qualifier != null) {
          reference = qualifier.getReference();
          name = qualifier.getText();
        }
      }
      pyFunction = PsiTreeUtil.getParentOfType(problemElement, PyFunction.class);
    }

    docstringGenerator.withParam(kind, name);

    final ASTNode nameNode = pyFunction.getNameNode();
    if ((pyFunction != null &&
         (problemElement instanceof PyParameter || reference != null && reference.resolve() instanceof PyParameter)) ||
        elementAt == nameNode.getPsi() || callExpression != null) {

      docstringGenerator.build();
    }


    docstringGenerator.startTemplate();
  }

  public boolean startInWriteAction() {
    return true;
  }
}