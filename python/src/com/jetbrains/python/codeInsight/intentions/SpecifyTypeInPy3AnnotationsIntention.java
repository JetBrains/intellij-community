/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import org.jetbrains.annotations.NotNull;

/**
 * User: ktisha
 *
 * Helps to specify type  in annotations in python3
 */
public class SpecifyTypeInPy3AnnotationsIntention extends TypeIntention {
  private String myText = PyBundle.message("INTN.specify.type.in.annotation");

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
    final PsiElement elementAt = PyUtil.findNonWhitespaceAtOffset(file, editor.getCaretModel().getOffset());
    final PyExpression problemElement = getProblemElement(elementAt);
    final PsiReference reference = problemElement == null ? null : problemElement.getReference();

    final PsiElement resolved = reference != null ? reference.resolve() : null;
    final PyNamedParameter parameter = getParameter(problemElement, resolved);

    if (parameter != null) {
      annotateParameter(project, editor, parameter);
    }
    else {
      annotateReturnType(project, editor.getDocument(), elementAt);
    }
  }

  private static void annotateParameter(Project project, Editor editor, @NotNull PyNamedParameter parameter) {
    final PyExpression defaultParamValue = parameter.getDefaultValue();

    final String name = StringUtil.notNullize(parameter.getName());
    final PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);

    final String defaultParamText = defaultParamValue == null ? null : defaultParamValue.getText();
    final PyNamedParameter namedParameter = elementGenerator.createParameter(name, defaultParamText, PyNames.OBJECT,
                                                                             LanguageLevel.forElement(parameter));
    assert namedParameter != null;
    parameter = (PyNamedParameter)parameter.replace(namedParameter);
    parameter = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(parameter);
    editor.getCaretModel().moveToOffset(parameter.getTextOffset());
    final PyAnnotation annotation = parameter.getAnnotation();
    if (annotation != null) {
      final PyExpression annotationValue = annotation.getValue();

      final TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(parameter);
      assert annotationValue != null : "Generated parameter must have annotation";
      final int replacementStart = annotation.getStartOffsetInParent() + annotationValue.getStartOffsetInParent();
      builder.replaceRange(TextRange.create(replacementStart,
                                            replacementStart + annotationValue.getTextLength()), PyNames.OBJECT);
      final Template template = ((TemplateBuilderImpl)builder).buildInlineTemplate();
      TemplateManager.getInstance(project).startTemplate(editor, template);
    }
  }

  private void annotateReturnType(Project project, Document document, PsiElement resolved) {
    PyCallable callable = getCallable(resolved);

    if (callable instanceof PyFunction) {
      final String annotationText = " -> " + PyNames.OBJECT;
      
      final PsiElement prevElem = PyPsiUtils.getPrevNonCommentSibling(((PyFunction)callable).getStatementList(), true);
      assert prevElem != null;

      final PsiDocumentManager manager = PsiDocumentManager.getInstance(project);
      try {
        final TextRange range = prevElem.getTextRange();
        manager.doPostponedOperationsAndUnblockDocument(document);
        if (prevElem.getNode().getElementType() == PyTokenTypes.COLON) {
          document.insertString(range.getStartOffset(), annotationText);
        }
        else {
          document.insertString(range.getEndOffset(), annotationText + ":");
        }
      }
      finally {
        manager.commitDocument(document);
      }
      
      
      callable = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(callable);
      final PyAnnotation annotation = ((PyFunction)callable).getAnnotation();
      assert annotation != null;
      final PyExpression annotationValue = annotation.getValue();
      assert annotationValue != null : "Generated function must have annotation";
      final int offset = annotationValue.getTextOffset();

      final TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(annotationValue);
      builder.replaceRange(TextRange.create(0, PyNames.OBJECT.length()), PyNames.OBJECT);
      final Template template = ((TemplateBuilderImpl)builder).buildInlineTemplate();
      final OpenFileDescriptor descriptor = new OpenFileDescriptor(
        project,
        callable.getContainingFile().getVirtualFile(),
        offset
      );
      final Editor targetEditor = FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
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

  private static boolean isDefinedInAnnotation(PyParameter parameter) {
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
    myText = isReturn ? PyBundle.message("INTN.specify.return.type.in.annotation") : PyBundle.message("INTN.specify.type.in.annotation");
  }
}