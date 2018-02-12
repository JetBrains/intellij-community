/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.debugger.PySignature;
import com.jetbrains.python.debugger.PySignatureCacheManager;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

/**
 * User: ktisha
 * <p>
 * Helps to specify type  in annotations in python3
 */
public class SpecifyTypeInPy3AnnotationsIntention extends TypeIntention {
  private String myText = PyBundle.message("INTN.specify.type.in.annotation");

  @Override
  @NotNull
  public String getText() {
    return myText;
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INTN.specify.type.in.annotation");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (LanguageLevel.forElement(file).isPython2()) return false;
    return super.isAvailable(project, editor, file);
  }

  @Override
  public void doInvoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final PsiElement elementAt = PyUtil.findNonWhitespaceAtOffset(file, editor.getCaretModel().getOffset());
    final PyExpression problemElement = getProblemElement(elementAt);
    final PsiReference reference = problemElement == null ? null : problemElement.getReference();

    final PsiElement resolved = reference != null ? reference.resolve() : null;
    final PyNamedParameter parameter = getParameter(problemElement, resolved);

    if (parameter != null) {
      annotateParameter(project, editor, parameter, true);
    }
    else {
      StreamEx
        .of(getMultiCallable(elementAt))
        .select(PyFunction.class)
        .forEach(function -> annotateReturnType(project, function, true));
    }
  }

  static PyNamedParameter annotateParameter(Project project,
                                            Editor editor,
                                            @NotNull PyNamedParameter parameter,
                                            boolean createTemplate) {
    final PyExpression defaultParamValue = parameter.getDefaultValue();

    final String paramName = StringUtil.notNullize(parameter.getName());
    final PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);

    final String defaultParamText = defaultParamValue == null ? null : defaultParamValue.getText();

    String paramType = parameterType(parameter);


    final PyNamedParameter namedParameter = elementGenerator.createParameter(paramName, defaultParamText, paramType,
                                                                             LanguageLevel.forElement(parameter));
    assert namedParameter != null;
    parameter = (PyNamedParameter)parameter.replace(namedParameter);
    parameter = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(parameter);
    editor.getCaretModel().moveToOffset(parameter.getTextOffset());
    final PyAnnotation annotation = parameter.getAnnotation();
    if (annotation != null && createTemplate) {
      final PyExpression annotationValue = annotation.getValue();

      final TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(parameter);
      assert annotationValue != null : "Generated parameter must have annotation";
      final int replacementStart = annotation.getStartOffsetInParent() + annotationValue.getStartOffsetInParent();
      builder.replaceRange(TextRange.create(replacementStart,
                                            replacementStart + annotationValue.getTextLength()), paramType);
      final Template template = ((TemplateBuilderImpl)builder).buildInlineTemplate();
      TemplateManager.getInstance(project).startTemplate(editor, template);
    }

    return parameter;
  }

  static String parameterType(PyParameter parameter) {
    String paramType = PyNames.OBJECT;

    PyFunction function = PsiTreeUtil.getParentOfType(parameter, PyFunction.class);
    if (function != null) {
      final PySignature signature = PySignatureCacheManager.getInstance(parameter.getProject()).findSignature(
        function);
      String parameterName = parameter.getName();
      if (signature != null && parameterName != null) {
        paramType = ObjectUtils.chooseNotNull(signature.getArgTypeQualifiedName(parameterName), paramType);
      }
    }
    return paramType;
  }


  static String returnType(@NotNull PyFunction function) {
    String returnType = PyNames.OBJECT;
    final PySignature signature = PySignatureCacheManager.getInstance(function.getProject()).findSignature(function);
    if (signature != null) {
      returnType = ObjectUtils.chooseNotNull(signature.getReturnTypeQualifiedName(), returnType);
    }
    return returnType;
  }

  public static PyExpression annotateReturnType(Project project, PyFunction function, boolean createTemplate) {
    String returnType = returnType(function);

    final String annotationText = "-> " + returnType;

    PyFunction annotatedFunction = PyUtil.updateDocumentUnblockedAndCommitted(function, document -> {
      final PyAnnotation oldAnnotation = function.getAnnotation();
      if (oldAnnotation != null) {
        final TextRange oldRange = oldAnnotation.getTextRange();
        document.replaceString(oldRange.getStartOffset(), oldRange.getEndOffset(), annotationText);
      }
      else {
        final PsiElement prevElem = PyPsiUtils.getPrevNonCommentSibling(function.getStatementList(), true);
        assert prevElem != null;
        final TextRange range = prevElem.getTextRange();
        if (prevElem.getNode().getElementType() == PyTokenTypes.COLON) {
          document.insertString(range.getStartOffset(), " " + annotationText);
        }
        else {
          document.insertString(range.getEndOffset(), " " + annotationText + ":");
        }
      }
      return CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(function);
    });

    if (annotatedFunction == null) {
      return null;
    }

    final PyAnnotation annotation = annotatedFunction.getAnnotation();
    assert annotation != null;
    final PyExpression annotationValue = annotation.getValue();
    assert annotationValue != null : "Generated function must have annotation";

    if (createTemplate) {
      final int offset = annotationValue.getTextOffset();

      final TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(annotationValue);
      builder.replaceRange(TextRange.create(0, returnType.length()), returnType);
      final Template template = ((TemplateBuilderImpl)builder).buildInlineTemplate();
      final OpenFileDescriptor descriptor = new OpenFileDescriptor(project, annotatedFunction.getContainingFile().getVirtualFile(), offset);
      final Editor targetEditor = FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
      if (targetEditor != null) {
        targetEditor.getCaretModel().moveToOffset(offset);
        TemplateManager.getInstance(project).startTemplate(targetEditor, template);
      }
    }
    return annotationValue;
  }

  @Override
  protected boolean isParamTypeDefined(PyParameter parameter) {
    return isDefinedInAnnotation(parameter);
  }

  private static boolean isDefinedInAnnotation(PyParameter parameter) {
    if (LanguageLevel.forElement(parameter).isPython2()) {
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