// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.intentions;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInsight.template.impl.ConstantNode;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.ModCommandExecutor;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.ModTemplateBuilder;
import com.intellij.modcommand.Presentation;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.codeInsight.intentions.PyTypeHintGenerationUtil.AnnotationInfo;
import com.jetbrains.python.documentation.doctest.PyDoctestFile;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyAnnotation;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyIndentUtil;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyStatementList;
import com.jetbrains.python.psi.PyUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@ApiStatus.Internal
public final class PyAnnotateTypesIntention implements ModCommandAction {

  @Override
  public @NotNull String getFamilyName() {
    return PyPsiBundle.message("INTN.NAME.add.type.hints.for.function");
  }

  @Override
  public @Nullable Presentation getPresentation(@NotNull ActionContext context) {
    final PsiFile psiFile = context.file();
    if (!(psiFile instanceof PyFile) || psiFile instanceof PyDoctestFile) return null;

    final PyFunction function = findSuitableFunction(context);
    if (function == null) return null;

    if (function.getTypeComment() != null) {
      return null;
    }

    final PyAnnotation annotation = function.getAnnotation();
    if (annotation != null) {
      boolean allParametersAnnotated = ContainerUtil.and(function.getParameterList().getParameters(),
                                                         it -> it instanceof PyNamedParameter &&
                                                               ((PyNamedParameter)it).getAnnotation() != null);
      if (allParametersAnnotated) {
        return null;
      }
    }

    return Presentation.of(PyPsiBundle.message("INTN.add.type.hints.for.function", function.getName()));
  }

  @Override
  public @NotNull ModCommand perform(@NotNull ActionContext context) {
    final PyFunction function = findSuitableFunction(context);
    return function != null ? createCommand(context, function) : ModCommand.nop();
  }

  private static @Nullable PyFunction findSuitableFunction(@NotNull ActionContext context) {
    return TypeIntention.findOnlySuitableFunction(context.file(), context.offset(), input -> true);
  }

  /**
   * Adds type hints to {@code function} and starts a template on them, as the intention does.
   * {@code editor} can be null; the template then opens an editor for the file of {@code function}.
   */
  public static void annotateTypes(@Nullable Editor editor, @NotNull PyFunction function) {
    final ActionContext context = ActionContext.from(editor, function.getContainingFile());
    ModCommandExecutor.getInstance().executeInteractively(context, createCommand(context, function), editor);
  }

  /** Computes the types on the physical {@code function}, because a copy has no collected run-time signature. */
  private static @NotNull ModCommand createCommand(@NotNull ActionContext context, @NotNull PyFunction function) {
    final AnnotationInfo returnType = SpecifyTypeInPy3AnnotationsIntention.returnType(function);
    final PyParameter[] params = function.getParameterList().getParameters();
    final List<String> paramTypes = ContainerUtil.map(params, SpecifyTypeInPy3AnnotationsIntention::parameterType);
    final Project project = context.project();
    if (isPy3k(function.getContainingFile())) {
      return ModCommand.psiUpdate(context, updater -> generatePy3kTypeAnnotations(project, updater.getWritable(function), paramTypes,
                                                                                    returnType, updater));
    }
    return ModCommand.psiUpdate(context, updater -> generateTypeCommentAnnotations(updater.getWritable(function), paramTypes,
                                                                                     returnType.getAnnotationText(), updater));
  }

  private static void generateTypeCommentAnnotations(@NotNull PyFunction function,
                                                     @NotNull List<String> paramTypes,
                                                     @NotNull String returnType,
                                                     @NotNull ModPsiUpdater updater) {

    StringBuilder replacementTextBuilder = new StringBuilder("# type: (");

    PyParameter[] params = function.getParameterList().getParameters();

    List<Pair<Integer, String>> templates = new ArrayList<>();

    for (int i = 0; i < params.length; i++) {
      if (!params[i].isSelf()) {
        String type = paramTypes.get(i);

        templates.add(Pair.create(replacementTextBuilder.length(), type));

        replacementTextBuilder.append(type);

        if (i < params.length - 1) {
          replacementTextBuilder.append(", ");
        }
      }
    }

    replacementTextBuilder.append(") -> ");

    templates.add(Pair.create(replacementTextBuilder.length(), returnType));

    replacementTextBuilder.append(returnType);

    final PyStatementList statements = function.getStatementList();
    final String indentation = PyIndentUtil.getElementIndent(statements);
    replacementTextBuilder.insert(0, indentation);
    replacementTextBuilder.insert(0, "\n");

    final PsiElement beforeStatements = statements.getPrevSibling();
    final int offset = beforeStatements.getTextRange().getStartOffset() + (":".equals(beforeStatements.getText()) ? 1 : 0);
    final String replacementText = replacementTextBuilder.toString();
    PyUtil.updateDocumentUnblockedAndCommitted(function, document -> {
      document.insertString(offset, replacementText);
    });

    PsiElement element = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(function);

    while (element != null && !element.getText().contains(replacementText)) {
      element = element.getParent();
    }

    if (element != null) {
      final ModTemplateBuilder builder = updater.templateBuilder();
      final int commentStart = offset - element.getTextRange().getStartOffset() + replacementText.indexOf('#');
      for (int i = 0; i < templates.size(); i++) {
        final Pair<Integer, String> template = templates.get(i);
        builder.field(element, TextRange.from(commentStart + template.first, template.second.length()), "TYPE" + i,
                      new ConstantNode(template.second));
      }
    }
  }

  private static boolean isPy3k(PsiFile file) {
    return !LanguageLevel.forElement(file).isPython2();
  }

  private static void generatePy3kTypeAnnotations(@NotNull Project project,
                                                  @NotNull PyFunction originalFunction,
                                                  @NotNull List<String> paramTypes,
                                                  @NotNull AnnotationInfo returnTypeInfo,
                                                  @NotNull ModPsiUpdater updater) {
    // The return type goes in through the document, so the reparse can replace the function element.
    final PyExpression annotatedReturnType = SpecifyTypeInPy3AnnotationsIntention.annotateReturnType(originalFunction, returnTypeInfo, null);
    final PyFunction function = annotatedReturnType != null
                                ? PsiTreeUtil.getParentOfType(annotatedReturnType, PyFunction.class)
                                : originalFunction;
    if (function == null) return;

    PyParameter[] params = function.getParameterList().getParameters();

    for (int i = params.length - 1; i >= 0; i--) {
      if (params[i] instanceof PyNamedParameter && !params[i].isSelf()) {
        params[i] = SpecifyTypeInPy3AnnotationsIntention.annotateParameter(project, (PyNamedParameter)params[i], paramTypes.get(i), null);
      }
    }

    final ModTemplateBuilder builder = updater.templateBuilder();
    for (PyParameter param : params) {
      if (param instanceof PyNamedParameter namedParameter && !param.isSelf()) {
        PyAnnotation annotation = namedParameter.getAnnotation();
        if (annotation != null) {
          PyExpression annotationValue = annotation.getValue();
          if (annotationValue != null) {
            builder.field(annotationValue, annotationValue.getText());
          }
        }
      }
    }
    // The edits of the parameters reformat the header, so find the return type again.
    final PyAnnotation returnAnnotation = function.getAnnotation();
    final PyExpression returnType = returnAnnotation != null ? returnAnnotation.getValue() : null;
    if (returnType != null) {
      builder.field(returnType, returnType.getText());
    }
  }
}
