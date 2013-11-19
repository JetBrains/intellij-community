/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.codeInsight.intentions;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInsight.template.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * User: ktisha
 *
 * Helps to specify type  in annotations in python3
 */
public class SpecifyTypeInPy3AnnotationsIntention extends TypeIntention {
  private String myText = PyBundle.message("INTN.specify.type.in.annotation");
  public SpecifyTypeInPy3AnnotationsIntention() {
  }

  @NotNull
  public String getText() {
    return myText;
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INTN.specify.type.in.annotation");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!LanguageLevel.forElement(file).isPy3K()) return false;
    return super.isAvailable(project, editor, file);
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiElement elementAt = PyUtil.findNonWhitespaceAtOffset(file, editor.getCaretModel().getOffset());
    PyExpression problemElement = getProblemElement(elementAt);
    PsiReference reference = problemElement == null? null : problemElement.getReference();

    final PsiElement resolved = reference != null? reference.resolve() : null;
    PyParameter parameter = getParameter(problemElement, resolved);

    if (parameter != null) {
      annotateParameter(project, editor, parameter);
    }
    else {
      annotateReturnType(project, elementAt);
    }
  }

  private static void annotateParameter(Project project, Editor editor, PyParameter parameter) {
    PyExpression defaultParamValue = parameter instanceof PyNamedParameter? parameter.getDefaultValue() : null;

    final String name = StringUtil.notNullize(parameter.getName());
    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);

    String defaultParamText = defaultParamValue == null? null: defaultParamValue.getText();
    final PyNamedParameter namedParameter = elementGenerator.createParameter(name, defaultParamText, PyNames.OBJECT, LanguageLevel.forElement(parameter));
    assert namedParameter != null;
    parameter = (PyParameter)parameter.replace(namedParameter);
    parameter = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(parameter);
    editor.getCaretModel().moveToOffset(parameter.getTextOffset());
    PyAnnotation annotation = parameter instanceof PyNamedParameter? ((PyNamedParameter)parameter).getAnnotation() : null;
    if (annotation != null) {
      PyExpression annotationValue = annotation.getValue();

      final TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(parameter);
      int replacementStart = annotation.getStartOffsetInParent() + annotationValue.getStartOffsetInParent();
      builder.replaceRange(TextRange.create(replacementStart,
                                            replacementStart + annotationValue.getTextLength()), PyNames.OBJECT);
      Template template = ((TemplateBuilderImpl)builder).buildInlineTemplate();
      TemplateManager.getInstance(project).startTemplate(editor, template);
    }
  }

  private void annotateReturnType(Project project, PsiElement resolved) {
    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);

    Callable callable = getCallable(resolved);

    if (callable instanceof PyFunction) {
      final String functionSignature = "def " + callable.getName() + callable.getParameterList().getText();
      String functionText = functionSignature +
                          " -> object:";
      final PyStatementList statementList = ((PyFunction)callable).getStatementList();
      assert statementList != null;
      for (PyStatement st : statementList.getStatements()) {
        functionText = functionText + "\n\t" + st.getText();
      }
      final PyFunction function = elementGenerator.createFromText(LanguageLevel.forElement(callable), PyFunction.class,
                                                                  functionText);
      callable = (PyFunction)callable.replace(function);
      callable = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(callable);

      final PyAnnotation annotation = ((PyFunction)callable).getAnnotation();
      assert annotation != null;
      final PyExpression annotationValue = annotation.getValue();
      final int offset = annotationValue.getTextOffset();

      final TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(annotationValue);
      builder.replaceRange(TextRange.create(0, PyNames.OBJECT.length()), PyNames.OBJECT);
      Template template = ((TemplateBuilderImpl)builder).buildInlineTemplate();
      OpenFileDescriptor descriptor = new OpenFileDescriptor(
        project,
        callable.getContainingFile().getVirtualFile(),
        offset
      );
      Editor targetEditor = FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
      if (targetEditor != null) {
        targetEditor.getCaretModel().moveToOffset(offset);
        TemplateManager.getInstance(project).startTemplate(targetEditor, template);
      }
    }
  }

  @Override
  protected boolean isParamTypeDefined(PyParameter parameter) {
    return isDefinedInAnnotation(parameter);
  }

  private boolean isDefinedInAnnotation(PyParameter parameter) {
    if (LanguageLevel.forElement(parameter).isOlderThan(LanguageLevel.PYTHON30)) {
      return false;
    }
    if (parameter instanceof PyNamedParameter && (((PyNamedParameter)parameter).getAnnotation() != null)) return true;
    return false;
  }

  @Override
  protected boolean isReturnTypeDefined(@NotNull PyFunction function) {
    return function.getAnnotation() != null;
  }

  @Override
  protected void updateText(boolean isReturn) {
    myText = isReturn? PyBundle.message("INTN.specify.return.type.in.annotation") : PyBundle.message("INTN.specify.type.in.annotation");
  }
}