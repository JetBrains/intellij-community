package com.jetbrains.python.codeInsight.intentions;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyNames;
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
 *
 * Common part for type specifying intentions
 */
public abstract class TypeIntention implements IntentionAction {

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    updateText(false);

    PsiElement elementAt = PyUtil.findNonWhitespaceAtOffset(file, editor.getCaretModel().getOffset());
    if (elementAt == null) return false;
    if (isAvailableForReturn(elementAt)) return true;

    final PyExpression problemElement = getProblemElement(elementAt);
    if (problemElement == null) return false;
    if (PsiTreeUtil.getParentOfType(problemElement, PyLambdaExpression.class) != null) {
      return false;
    }
    final PsiReference reference = problemElement.getReference();
    if (reference instanceof PsiPolyVariantReference) {
      final ResolveResult[] results = ((PsiPolyVariantReference)reference).multiResolve(false);
      if (results.length != 1) return false;
    }
    final VirtualFile virtualFile = problemElement.getContainingFile().getVirtualFile();
    if (virtualFile != null) {
      if (ProjectRootManager.getInstance(project).getFileIndex().isInLibraryClasses(virtualFile)) {
        return false;
      }
    }
    return isTypeUndefined(problemElement);
  }

  @Nullable
  protected static PyExpression getProblemElement(PsiElement elementAt) {
    PyExpression problemElement = PsiTreeUtil.getParentOfType(elementAt, PyNamedParameter.class, PyReferenceExpression.class);
    if (problemElement == null) return null;
    if (problemElement instanceof PyQualifiedExpression) {
      final PyExpression qualifier = ((PyQualifiedExpression)problemElement).getQualifier();
      if (qualifier != null && !qualifier.getText().equals(PyNames.CANONICAL_SELF)) {
        problemElement = qualifier;
      }
    }
    return problemElement;
  }

  protected abstract void updateText(boolean isReturn);

  private static boolean isTypeUndefined(PyExpression problemElement) {
    final PyType type = problemElement.getType(TypeEvalContext.fastStubOnly(problemElement.getContainingFile()));
    if (type == null || type instanceof PyReturnTypeReference || type instanceof PyDynamicallyEvaluatedType) {
      PsiReference reference = problemElement.getReference();
      if (problemElement instanceof PyQualifiedExpression) {
        final PyExpression qualifier = ((PyQualifiedExpression)problemElement).getQualifier();
        if (qualifier != null && !qualifier.getText().equals(PyNames.CANONICAL_SELF)) reference = qualifier.getReference();
      }

      if (isDefinedInDocstring(problemElement, reference)) return false;
      return !isDefinedInAnnotation(problemElement, reference);
    }
    return false;
  }

  private static boolean isDefinedInAnnotation(PyExpression problemElement, PsiReference reference) {
    if (LanguageLevel.forElement(problemElement).isOlderThan(LanguageLevel.PYTHON30)) {
      return false;
    }
    final PsiElement resolved = reference != null? reference.resolve() : null;
    PyParameter parameter = getParameter(problemElement, resolved);

    if (parameter instanceof PyNamedParameter && (((PyNamedParameter)parameter).getAnnotation() != null)) return true;

    if (resolved instanceof PyTargetExpression) { // return type
      final PyExpression assignedValue = ((PyTargetExpression)resolved).findAssignedValue();
      if (assignedValue instanceof PyCallExpression) {
        final PyExpression callee = ((PyCallExpression)assignedValue).getCallee();
        if (callee != null) {
          final PsiReference psiReference = callee.getReference();
          if (psiReference != null && psiReference.resolve() == null) return false;
        }
        final Callable callable = ((PyCallExpression)assignedValue).resolveCalleeFunction(getResolveContext(problemElement));

        if (callable instanceof PyFunction && ((PyFunction)callable).getAnnotation() != null) return true;
      }
    }
    return false;
  }

  @Nullable
  protected static PyParameter getParameter(PyExpression problemElement, PsiElement resolved) {
    PyParameter parameter = problemElement instanceof PyParameter? (PyParameter)problemElement : null;
    if (resolved instanceof PyParameter)
      parameter = (PyParameter)resolved;
    return parameter;
  }

  private static boolean isDefinedInDocstring(PyExpression problemElement, PsiReference reference) {
    PyFunction pyFunction = PsiTreeUtil.getParentOfType(problemElement, PyFunction.class);
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
        return structuredDocString != null && structuredDocString.getParamType(name) != null;
      }
      return false;
    }
    return false;
  }

  private boolean isAvailableForReturn(PsiElement elementAt) {
    PyCallExpression callExpression = getCallExpression(elementAt);

    if (callExpression != null) {
      final PyExpression callee = callExpression.getCallee();
      if (callee instanceof PyQualifiedExpression) {
        final TypeEvalContext context = TypeEvalContext.fastStubOnly(callee.getContainingFile());
        final Callable pyFunction = callExpression.resolveCalleeFunction(PyResolveContext.defaultContext().withTypeEvalContext(context));
        if (pyFunction instanceof PyFunction) {
          PyType type = pyFunction.getReturnType(context, (PyQualifiedExpression)callee);
          if (type == null || type instanceof PyReturnTypeReference) {
            final PsiReference reference = callee.getReference();
            if (reference instanceof PsiPolyVariantReference) {
              final ResolveResult[] results = ((PsiPolyVariantReference)reference).multiResolve(false);
              if (results.length == 1) {
                final PsiElement result = results[0].getElement();
                if (result == null) return false;
                final PsiFile psiFile = result.getContainingFile();
                if (psiFile == null) return false;
                final VirtualFile virtualFile = psiFile.getVirtualFile();
                if (virtualFile != null) {
                  if (ProjectRootManager.getInstance(psiFile.getProject()).getFileIndex().isInLibraryClasses(virtualFile)) {
                    return false;
                  }
                }
                updateText(true);
                return true;
              }
            }
          }
        }
      }
    }
    PyFunction parentFunction = PsiTreeUtil.getParentOfType(elementAt, PyFunction.class);
    if (parentFunction != null) {
      final ASTNode nameNode = parentFunction.getNameNode();
      if (nameNode != null && nameNode.getPsi() == elementAt) {
        updateText(true);
        return true;
      }
    }
    return false;
  }

  @Nullable
  protected static PyCallExpression getCallExpression(PsiElement elementAt) {
    PyCallExpression callExpression = PsiTreeUtil.getParentOfType(elementAt, PyCallExpression.class, false);
    if (callExpression == null) {
      final PyExpression problemElement = getProblemElement(elementAt);
      if (problemElement != null) {
        PsiReference reference = problemElement.getReference();
        final PsiElement resolved = reference != null? reference.resolve() : null;
        if (resolved instanceof PyTargetExpression) {
          final PyExpression assignedValue = ((PyTargetExpression)resolved).findAssignedValue();
          if (assignedValue instanceof PyCallExpression) {
            return (PyCallExpression)assignedValue;
          }
        }
      }

      PyAssignmentStatement assignmentStatement = PsiTreeUtil.getParentOfType(elementAt, PyAssignmentStatement.class);
      if (assignmentStatement != null) {
        final PyExpression assignedValue = assignmentStatement.getAssignedValue();
        if (assignedValue instanceof PyCallExpression) {
          return (PyCallExpression)assignedValue;
        }
      }
    }
    return callExpression;
  }

  @Nullable
  protected static Callable getCallable(PsiElement elementAt) {
    PyCallExpression callExpression = getCallExpression(elementAt);

    if (callExpression != null && elementAt != null) {
      final Callable callable = callExpression.resolveCalleeFunction(getResolveContext(elementAt));
      return callable == null ? PsiTreeUtil.getParentOfType(elementAt, PyFunction.class) : callable;
    }
    return PsiTreeUtil.getParentOfType(elementAt, PyFunction.class);
  }

  private static PyResolveContext getResolveContext(@NotNull PsiElement origin) {
    return PyResolveContext.defaultContext().withTypeEvalContext(TypeEvalContext.fastStubOnly(origin.getContainingFile()));
  }

  public boolean startInWriteAction() {
    return true;
  }
}