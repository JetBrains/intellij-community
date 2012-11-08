package com.jetbrains.python.codeInsight.intentions;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.debugger.PySignature;
import com.jetbrains.python.debugger.PySignatureCacheManager;
import com.jetbrains.python.documentation.PyDocstringGenerator;
import com.jetbrains.python.documentation.StructuredDocString;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyDynamicallyEvaluatedType;
import com.jetbrains.python.psi.types.PyReturnTypeReference;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: ktisha
 * <p/>
 * Helps to specify type
 */
public class SpecifyTypeInDocstringIntention implements IntentionAction {
  private String myText = PyBundle.message("INTN.specify.type");

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
    PyExpression problemElement = PsiTreeUtil.getParentOfType(elementAt, PyNamedParameter.class, PyQualifiedExpression.class);
    if (checkAvailableForReturn(elementAt)) return true;


    if (problemElement == null) return false;
    if (problemElement instanceof PyQualifiedExpression) {
      final PyExpression qualifier = ((PyQualifiedExpression)problemElement).getQualifier();
      if (qualifier != null && !qualifier.getText().equals(PyNames.CANONICAL_SELF)) {
        problemElement = qualifier;
      }
    }
    if (PsiTreeUtil.getParentOfType(problemElement, PyLambdaExpression.class) != null) {
      return false;
    }
    final PyType type = problemElement.getType(TypeEvalContext.slow());
    return checkType(problemElement, type);
  }

  private boolean checkType(PyExpression problemElement, @Nullable PyType type) {
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
            if (qualifier != null) {
              name = qualifier.getText();
            }
          }
          StructuredDocString structuredDocString = StructuredDocString.parse(docstring);
          return structuredDocString.getParamType(name) == null;
        }
        return true;
      }
    }
    return false;
  }

  private boolean checkAvailableForReturn(PsiElement elementAt) {
    PyAssignmentStatement assignmentStatement = PsiTreeUtil.getParentOfType(elementAt, PyAssignmentStatement.class);
    if (assignmentStatement != null) {
      final PyExpression assignedValue = assignmentStatement.getAssignedValue();
      if (assignedValue != null) {
        PyType type = assignedValue.getType(TypeEvalContext.slow());
        if (type == null || type instanceof PyReturnTypeReference) {
          final PyCallExpression callExpression = PsiTreeUtil.getParentOfType(assignedValue, PyCallExpression.class, false);
          if (callExpression != null) {
            final PyFunction function = (PyFunction)callExpression.resolveCalleeFunction(PyResolveContext.defaultContext());
            if (function != null) {
              myText = PyBundle.message("INTN.specify.return.type");
              return true;
            }
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
    return false;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiElement elementAt = PyUtil.findNonWhitespaceAtOffset(file, editor.getCaretModel().getOffset());

    String kind = "type";
    PyFunction pyFunction = PsiTreeUtil.getParentOfType(elementAt, PyFunction.class);

    PyExpression problemElement = PsiTreeUtil.getParentOfType(elementAt, PyNamedParameter.class, PyQualifiedExpression.class);
    PyAssignmentStatement assignmentStatement = PsiTreeUtil.getParentOfType(elementAt, PyAssignmentStatement.class);
    PyCallExpression callExpression = null;
    if (assignmentStatement != null) {
      final PyExpression assignedValue = assignmentStatement.getAssignedValue();

      if (assignedValue != null) {
        callExpression = PsiTreeUtil.getParentOfType(assignedValue, PyCallExpression.class, false);
        if (callExpression != null) {
        PyType pyType = assignedValue.getType(TypeEvalContext.slow());
          if (pyType == null || pyType instanceof PyReturnTypeReference) {
            pyFunction = (PyFunction)callExpression.resolveCalleeFunction(PyResolveContext.defaultContext());
            if (pyFunction != null) {
              problemElement = null;
              kind = "rtype";
            }
          }
        }
      }
    }
    if (pyFunction != null) {
      final ASTNode nameNode = pyFunction.getNameNode();
      if (nameNode != null && nameNode.getPsi() == elementAt) {
        kind = "rtype";
      }
    }

    generateDocstring(elementAt, kind, callExpression, pyFunction, problemElement);
  }

  private void generateDocstring(PsiElement elementAt, String kind,
                                 @Nullable PyCallExpression callExpression,
                                 PyFunction pyFunction,
                                 PyExpression problemElement) {
    PsiReference reference = null;

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
    if (pyFunction == null || name == null) return;

    PyDocstringGenerator docstringGenerator = new PyDocstringGenerator(pyFunction);

    PySignature signature = PySignatureCacheManager.getInstance(pyFunction.getProject()).findSignature(pyFunction);
    if (signature != null) {
      docstringGenerator.withParamTypedByQualifiedName(kind, name, signature.getArgTypeQualifiedName(name), pyFunction);
    }
    else {
      docstringGenerator.withParam(kind, name);
    }

    final ASTNode nameNode = pyFunction.getNameNode();
    if ((problemElement instanceof PyParameter || reference != null && reference.resolve() instanceof PyParameter) ||
        (nameNode != null && elementAt == nameNode.getPsi()) || callExpression != null) {
      docstringGenerator.build();
    }

    docstringGenerator.startTemplate();
  }

  public boolean startInWriteAction() {
    return true;
  }
}