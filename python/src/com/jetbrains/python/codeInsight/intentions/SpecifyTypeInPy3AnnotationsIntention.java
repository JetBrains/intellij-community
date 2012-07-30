package com.jetbrains.python.codeInsight.intentions;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.template.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
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

    PyExpression problemElement = PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset() - 1),
                                                              PyNamedParameter.class);
    if (problemElement == null)
      problemElement = PsiTreeUtil.getTopmostParentOfType(file.findElementAt(editor.getCaretModel().getOffset() - 1),
                                                          PyQualifiedExpression.class);
    if (problemElement == null) return false;
    if (problemElement instanceof PyQualifiedExpression) {
      final PyExpression qualifier = ((PyQualifiedExpression)problemElement).getQualifier();
      if (qualifier != null && !qualifier.getText().equals(PyNames.CANONICAL_SELF)) {
        problemElement = qualifier;
      }
    }
    final PyType type = problemElement.getType(TypeEvalContext.fast());
    if (type == null || type instanceof PyReturnTypeReference) {
      PyFunction pyFunction = PsiTreeUtil.getParentOfType(problemElement, PyFunction.class);
      PsiReference reference = problemElement.getReference();
      if (problemElement instanceof PyQualifiedExpression) {
        final PyExpression qualifier = ((PyQualifiedExpression)problemElement).getQualifier();
        if (qualifier != null && !qualifier.getText().equals(PyNames.CANONICAL_SELF)) reference = qualifier.getReference();
      }
      if (pyFunction != null && (problemElement instanceof PyParameter || reference != null && reference.resolve() instanceof PyParameter))
        return true;
    }
    return false;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PyExpression problemElement = PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset() - 1), PyNamedParameter.class);
    if (problemElement == null)
      problemElement = PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()-1), PyExpression.class);
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
      if (problemElement instanceof PyParameter)
        parameter = (PyParameter)problemElement;
      else if (reference!= null && reference.resolve() instanceof PyParameter) {
        parameter = (PyParameter)reference.resolve();
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
    }
  }

  public boolean startInWriteAction() {
    return true;
  }
}