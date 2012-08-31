package com.jetbrains.python.codeInsight.intentions;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.template.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyReturnTypeReference;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

/**
 * User: ktisha
 *
 * Helps to specify type  in annotations in python3
 */
public class SpecifyTypeInPy3AnnotationsIntention implements IntentionAction {

  public SpecifyTypeInPy3AnnotationsIntention() {
  }

  @NotNull
  public String getText() {
    return PyBundle.message("INTN.specify.type.in.annotation");
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INTN.specify.type.in.annotation");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!LanguageLevel.forElement(file).isPy3K()) return false;

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
    if (type == null || type instanceof PyReturnTypeReference) {
      PsiReference reference = problemElement.getReference();
      if (problemElement instanceof PyQualifiedExpression) {
        final PyExpression qualifier = ((PyQualifiedExpression)problemElement).getQualifier();
        if (qualifier != null && !qualifier.getText().equals(PyNames.CANONICAL_SELF)) reference = qualifier.getReference();
      }
      PyParameter parameter = null;
      final PsiElement resolvedReference = reference != null?reference.resolve() : null;
      if (problemElement instanceof PyParameter)
        parameter = (PyParameter)problemElement;
      else if (resolvedReference instanceof PyParameter)
        parameter = (PyParameter)resolvedReference;
      if (parameter instanceof PyNamedParameter && (((PyNamedParameter)parameter).getAnnotation() != null ||
        parameter.getDefaultValue() != null)) return false;
      if (parameter != null)
        return true;
      else {
        if (resolvedReference instanceof PyTargetExpression) {
          final PyExpression assignedValue = ((PyTargetExpression)resolvedReference).findAssignedValue();
          if (assignedValue instanceof PyCallExpression) {
            final PyExpression callee = ((PyCallExpression)assignedValue).getCallee();
            if (callee != null) {
              final PsiReference psiReference = callee.getReference();
              if (psiReference != null && psiReference.resolve() == null) return false;
            }
            final Callable callable = ((PyCallExpression)assignedValue).resolveCalleeFunction(PyResolveContext.defaultContext());

            if (callable instanceof PyFunction && ((PyFunction)callable).getAnnotation() == null) return true;
          }
        }
      }
    }
    return false;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PyExpression problemElement = PyUtil.findProblemElement(editor, file, PyNamedParameter.class, PyQualifiedExpression.class);
    if (problemElement != null) {
      String name = problemElement.getName();
      PsiReference reference = problemElement.getReference();
      if (problemElement instanceof PyQualifiedExpression) {
        final PyExpression qualifier = ((PyQualifiedExpression)problemElement).getQualifier();
        if (qualifier != null) {
          reference = qualifier.getReference();
          name = qualifier.getText();
        }
      }
      PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);

      PyParameter parameter = null;
      final PsiElement resolvedReference = reference != null? reference.resolve() : null;
      if (problemElement instanceof PyParameter)
        parameter = (PyParameter)problemElement;
      else {
        if (resolvedReference instanceof PyParameter) {
          parameter = (PyParameter)resolvedReference;
        }
      }
      if (parameter != null && name != null) {
        final PyFunction function =
          elementGenerator.createFromText(LanguageLevel.forElement(problemElement), PyFunction.class,
                                          "def foo(" + name + ": object):\n\tpass");
        final PyNamedParameter namedParameter = function.getParameterList().findParameterByName(name);
        assert namedParameter != null;
        parameter = (PyParameter)parameter.replace(namedParameter);
        parameter = CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(parameter);
        editor.getCaretModel().moveToOffset(parameter.getTextOffset());

        final TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(parameter);
        builder.replaceRange(TextRange.create(parameter.getTextLength()-PyNames.OBJECT.length(), parameter.getTextLength()), PyNames.OBJECT);
        Template template = ((TemplateBuilderImpl)builder).buildInlineTemplate();
        TemplateManager.getInstance(project).startTemplate(editor, template);
      }
      else {    //return type
        if (resolvedReference instanceof PyTargetExpression) {
          final PyExpression assignedValue = ((PyTargetExpression)resolvedReference).findAssignedValue();
          if (assignedValue instanceof PyCallExpression) {
            Callable callable = ((PyCallExpression)assignedValue).resolveCalleeFunction(PyResolveContext.defaultContext());
            if (callable instanceof PyFunction && ((PyFunction)callable).getAnnotation() == null) {
              final String functionSignature = "def " + callable.getName() + callable.getParameterList().getText();
              String functionText = functionSignature +
                                  " -> object:";
              for (PyStatement st : ((PyFunction)callable).getStatementList().getStatements()) {
                functionText = functionText + "\n\t" + st.getText();
              }
              final PyFunction function = elementGenerator.createFromText(LanguageLevel.forElement(problemElement), PyFunction.class,
                                                                          functionText);
              callable = (PyFunction)callable.replace(function);
              callable = CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(callable);

              final PyExpression value = ((PyFunction)callable).getAnnotation().getValue();
              final int offset = value.getTextOffset();

              final TemplateBuilder builder = TemplateBuilderFactory.getInstance().
                createTemplateBuilder(value);
              builder.replaceRange(TextRange.create(0, PyNames.OBJECT.length()), PyNames.OBJECT);
              Template template = ((TemplateBuilderImpl)builder).buildInlineTemplate();
              OpenFileDescriptor descriptor = new OpenFileDescriptor(
                project,
                value.getContainingFile().getVirtualFile(),
                offset
              );
              Editor targetEditor = FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
              if (targetEditor != null) {
                targetEditor.getCaretModel().moveToOffset(offset);
                TemplateManager.getInstance(project).startTemplate(targetEditor, template);
              }
            }
          }
        }

      }
    }
  }

  public boolean startInWriteAction() {
    return true;
  }
}