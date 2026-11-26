// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.intentions;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInsight.template.impl.ConstantNode;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.codeInsight.intentions.PyTypeHintGenerationUtil.AnnotationInfo;
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.debugger.PySignature;
import com.jetbrains.python.debugger.PySignatureCacheManager;
import com.jetbrains.python.documentation.PythonDocumentationProvider;
import com.jetbrains.python.documentation.doctest.PyDoctestFile;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyAnnotation;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.impl.ParamHelper;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.types.PyLiteralType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: ktisha
 * <p>
 * Helps to specify type  in annotations in python3
 */
@ApiStatus.Internal
public final class SpecifyTypeInPy3AnnotationsIntention implements ModCommandAction {
  @Override
  public @NotNull String getFamilyName() {
    return PyPsiBundle.message("INTN.NAME.specify.type.in.annotation");
  }

  @Override
  public @Nullable Presentation getPresentation(@NotNull ActionContext context) {
    final PsiFile file = context.file();
    if (!(file instanceof PyFile) || file instanceof PyDoctestFile || LanguageLevel.forElement(file).isPython2()) return null;
    if (findParameter(context) != null) {
      return Presentation.of(PyPsiBundle.message("INTN.specify.type.in.annotation"));
    }
    if (findFunction(context) != null) {
      return Presentation.of(PyPsiBundle.message("INTN.specify.return.type.in.annotation"));
    }
    return null;
  }

  @Override
  public @NotNull ModCommand perform(@NotNull ActionContext context) {
    final PyNamedParameter parameter = findParameter(context);
    if (parameter != null) {
      final String paramType = parameterType(parameter);
      return ModCommand.psiUpdate(context, updater -> annotateParameter(context.project(), updater.getWritable(parameter), paramType, updater));
    }

    final PyFunction function = findFunction(context);
    if (function != null) {
      final AnnotationInfo returnType = returnType(function);
      return ModCommand.psiUpdate(context, updater -> annotateReturnType(updater.getWritable(function), returnType, updater));
    }
    return ModCommand.nop();
  }

  private static @Nullable PyNamedParameter findParameter(@NotNull ActionContext context) {
    return TypeIntention.findOnlySuitableParameter(context.file(), context.offset(), SpecifyTypeInPy3AnnotationsIntention::isParamTypeDefined);
  }

  private static @Nullable PyFunction findFunction(@NotNull ActionContext context) {
    return TypeIntention.findOnlySuitableFunction(context.file(), context.offset(), function -> !isReturnTypeDefined(function));
  }

  /**
   * Replaces {@code parameter} with a copy annotated with {@code paramType}, and returns the copy.
   * With an {@code updater}, moves the caret to the parameter and starts a template on the type.
   */
  public static PyNamedParameter annotateParameter(@NotNull Project project,
                                                   @NotNull PyNamedParameter parameter,
                                                   @NotNull String paramType,
                                                   @Nullable ModPsiUpdater updater) {
    final PyExpression defaultParamValue = parameter.getDefaultValue();

    final String paramName = ParamHelper.getNameInSignature(parameter);
    final PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);

    final String defaultParamText = defaultParamValue == null ? null : defaultParamValue.getText();

    final PyNamedParameter namedParameter = elementGenerator.createParameter(paramName, defaultParamText, paramType,
                                                                             LanguageLevel.forElement(parameter));
    assert namedParameter != null;
    parameter = (PyNamedParameter)parameter.replace(namedParameter);
    parameter = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(parameter);
    final PyAnnotation annotation = parameter.getAnnotation();
    if (annotation != null && updater != null) {
      updater.moveCaretTo(parameter.getTextOffset());
      final PyExpression annotationValue = annotation.getValue();
      assert annotationValue != null : "Generated parameter must have annotation";
      updater.templateBuilder().field(annotationValue, new ConstantNode(paramType));
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
    // Annotate with the wide type (e.g. `str`), not a one-return literal like `Literal["42"]`.
    inferredType = PyLiteralType.upcastLiteralToClass(inferredType);
    return new AnnotationInfo(
      PythonDocumentationProvider.getTypeHint(inferredType, context),
      PythonDocumentationProvider.getFullyQualifiedTypeHint(inferredType, context)
    );
  }

  /**
   * Annotates the return type of {@code function} with {@code returnTypeAnnotation}, and returns the annotation value.
   * With an {@code updater}, starts a template on the type.
   */
  public static @Nullable PyExpression annotateReturnType(@NotNull PyFunction function,
                                                          @NotNull AnnotationInfo returnTypeAnnotation,
                                                          @Nullable ModPsiUpdater updater) {
    final String returnTypeText = returnTypeAnnotation.getAnnotationText();
    final String annotationText = "-> " + returnTypeText;

    PyTypeHintGenerationUtil.addImportsForTypeAnnotations(returnTypeAnnotation.getFullyQualifiedTypeHints(), function);

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

    if (updater != null) {
      updater.templateBuilder()
        .field(annotationValue, TextRange.create(0, returnTypeText.length()), "TYPE", new ConstantNode(returnTypeText));
    }
    return annotationValue;
  }

  private static boolean isParamTypeDefined(@NotNull PyNamedParameter parameter) {
    if (parameter.getAnnotation() != null) return true;
    if (parameter.getTypeComment() != null) return true;
    PyFunction function = PsiTreeUtil.getParentOfType(parameter, PyFunction.class);
    return function != null && function.getTypeComment() != null;
  }

  private static boolean isReturnTypeDefined(@NotNull PyFunction function) {
    return function.getAnnotation() != null || function.getTypeComment() != null;
  }
}
