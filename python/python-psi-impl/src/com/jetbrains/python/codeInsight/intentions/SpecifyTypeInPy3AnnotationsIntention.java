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
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.template.TemplateBuilder;
import com.intellij.codeInsight.template.TemplateBuilderFactory;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonUiService;
import com.jetbrains.python.codeInsight.intentions.PyTypeHintGenerationUtil.AnnotationInfo;
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.debugger.PySignature;
import com.jetbrains.python.debugger.PySignatureCacheManager;
import com.jetbrains.python.documentation.PythonDocumentationProvider;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.ParamHelper;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

/**
 * User: ktisha
 * <p>
 * Helps to specify type  in annotations in python3
 */
public final class SpecifyTypeInPy3AnnotationsIntention extends TypeIntention {
  @Override
  public @NotNull String getFamilyName() {
    return PyPsiBundle.message("INTN.NAME.specify.type.in.annotation");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    if (LanguageLevel.forElement(psiFile).isPython2()) return false;
    return super.isAvailable(project, editor, psiFile);
  }

  @Override
  public void doInvoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final PyNamedParameter parameter = findOnlySuitableParameter(editor, file);
    if (parameter != null) {
      annotateParameter(project, editor, parameter);
      return;
    }

    final PyFunction function = findOnlySuitableFunction(editor, file);
    if (function != null) {
      annotateReturnType(project, function);
    }
  }

  private static void annotateParameter(Project project, Editor editor, @NotNull PyNamedParameter parameter) {
    if (!FileModificationService.getInstance().preparePsiElementForWrite(parameter)) return;
    WriteAction.run(() -> annotateParameter(project, editor, parameter, true));
  }

  private static void annotateReturnType(Project project, PyFunction function) {
    if (!FileModificationService.getInstance().preparePsiElementForWrite(function)) return;
    WriteAction.run(() -> annotateReturnType(project, function, true));
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  public static PyNamedParameter annotateParameter(Project project,
                                                   Editor editor,
                                                   @NotNull PyNamedParameter parameter,
                                                   boolean createTemplate) {
    final PyExpression defaultParamValue = parameter.getDefaultValue();

    final String paramName = ParamHelper.getNameInSignature(parameter);
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
      builder.replaceRange(TextRange.from(replacementStart, annotationValue.getTextLength()), paramType);
      builder.run(editor, true);
    }

    return parameter;
  }

  static String parameterType(PyParameter parameter) {
    String paramType = PyNames.OBJECT;

    if (parameter instanceof PyNamedParameter) {
      PyAnnotation annotation = ((PyNamedParameter)parameter).getAnnotation();
      if (annotation != null && annotation.getValue() != null) {
        return annotation.getValue().getText();
      }
    }

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


  static @NotNull AnnotationInfo returnType(@NotNull PyFunction function) {
    if (function.getAnnotation() != null && function.getAnnotation().getValue() != null) {
      return new AnnotationInfo(function.getAnnotation().getValue().getText());
    }

    final PySignature signature = PySignatureCacheManager.getInstance(function.getProject()).findSignature(function);
    if (signature != null) {
      final String qualifiedName = signature.getReturnTypeQualifiedName();
      if (qualifiedName != null) return new AnnotationInfo(qualifiedName);
    }

    final TypeEvalContext context = TypeEvalContext.userInitiated(function.getProject(), function.getContainingFile());
    PyType inferredType = context.getReturnType(function);
    if (function.isAsync()) {
      inferredType = Ref.deref(PyTypingTypeProvider.unwrapCoroutineReturnType(inferredType));
    }
    return new AnnotationInfo(PythonDocumentationProvider.getTypeHint(inferredType, context), inferredType);
  }

  public static PyExpression annotateReturnType(Project project, PyFunction function, boolean createTemplate) {
    AnnotationInfo returnTypeAnnotation = returnType(function);

    final String returnTypeText = returnTypeAnnotation.getAnnotationText();
    final String annotationText = "-> " + returnTypeText;

    final PsiFile file = function.getContainingFile();
    final TypeEvalContext context = TypeEvalContext.userInitiated(project, file);
    PyTypeHintGenerationUtil.addImportsForTypeAnnotations(returnTypeAnnotation.getTypes(), context, file);

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
      builder.replaceRange(TextRange.create(0, returnTypeText.length()), returnTypeText);
      final Editor targetEditor = PythonUiService.getInstance().openTextEditor(project, annotatedFunction.getContainingFile().getVirtualFile(), offset);
      if (targetEditor != null) {
        builder.run(targetEditor, true);
      }
    }
    return annotationValue;
  }

  @Override
  protected boolean isParamTypeDefined(@NotNull PyNamedParameter parameter) {
    if (parameter.getAnnotation() != null) return true;
    if (parameter.getTypeComment() != null) return true;
    PyFunction function = PsiTreeUtil.getParentOfType(parameter, PyFunction.class);
    return function != null && function.getTypeComment() != null;
  }

  @Override
  protected boolean isReturnTypeDefined(@NotNull PyFunction function) {
    return function.getAnnotation() != null || function.getTypeComment() != null;
  }

  @Override
  protected void updateText(boolean isReturn) {
    setText(isReturn ? PyPsiBundle.message("INTN.specify.return.type.in.annotation")
                     : PyPsiBundle.message("INTN.specify.type.in.annotation"));
  }
}
