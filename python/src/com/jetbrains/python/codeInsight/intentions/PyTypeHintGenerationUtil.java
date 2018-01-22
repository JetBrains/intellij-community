// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.intentions;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInsight.template.TemplateBuilder;
import com.intellij.codeInsight.template.TemplateBuilderFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

import static com.jetbrains.python.psi.PyUtil.as;

/**
 * @author Mikhail Golubev
 */
public class PyTypeHintGenerationUtil {
  private PyTypeHintGenerationUtil() {}

  public static void insertVariableAnnotation(@NotNull PyTargetExpression target, @NotNull String annotation, boolean startTemplate) {
    final LanguageLevel langLevel = LanguageLevel.forElement(target);
    if (langLevel.isOlderThan(LanguageLevel.PYTHON36)) {
      throw new IllegalArgumentException("Target '" + target.getText() + "' doesn't belong to Python 3.6+ project: " + langLevel);
    }

    final Project project = target.getProject();
    final PyAnnotationOwner createdAnnotationOwner;
    if (canUseInlineAnnotation(target)) {
      final SmartPointerManager manager = SmartPointerManager.getInstance(project);
      final PyAssignmentStatement assignment = (PyAssignmentStatement)target.getParent();
      final SmartPsiElementPointer<PyAssignmentStatement> pointer = manager.createSmartPsiElementPointer(assignment);
      PyUtil.updateDocumentUnblockedAndCommitted(target, document -> {
        document.insertString(target.getTextRange().getEndOffset(), ": " + annotation);
      });
      createdAnnotationOwner = pointer.getElement();
    }
    else {
      final PyElementGenerator generator = PyElementGenerator.getInstance(project);
      final String declarationText = target.getText() + ": " + annotation;
      final PyTypeDeclarationStatement declaration = generator.createFromText(langLevel, PyTypeDeclarationStatement.class, declarationText);
      final PyStatement statement = PsiTreeUtil.getParentOfType(target, PyStatement.class);
      assert statement != null;
      final PyAnnotationOwner inserted = (PyAnnotationOwner)statement.getParent().addBefore(declaration, statement);
      createdAnnotationOwner = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(inserted);
    }

    if (startTemplate && createdAnnotationOwner != null) {
      assert createdAnnotationOwner.getAnnotationValue() != null;

      final int initialCaretOffset = createdAnnotationOwner.getTextRange().getStartOffset();
      final VirtualFile updatedVirtualFile = createdAnnotationOwner.getContainingFile().getVirtualFile();
      final OpenFileDescriptor descriptor = new OpenFileDescriptor(project, updatedVirtualFile, initialCaretOffset);
      final Editor editor = FileEditorManager.getInstance(project).openTextEditor(descriptor, true);

      if (editor != null) {
        editor.getCaretModel().moveToOffset(initialCaretOffset);
        final TemplateBuilder templateBuilder = TemplateBuilderFactory.getInstance().createTemplateBuilder(createdAnnotationOwner);
        final String replacementText = ApplicationManager.getApplication().isUnitTestMode() ? "[" + annotation + "]" : annotation;
        //noinspection ConstantConditions
        templateBuilder.replaceElement(createdAnnotationOwner.getAnnotation().getValue(), replacementText);
        templateBuilder.run(editor, true);
      }
    }
  }

  private static boolean canUseInlineAnnotation(@NotNull PyTargetExpression target) {
    final PyAssignmentStatement assignment = as(target.getParent(), PyAssignmentStatement.class);
    return assignment != null && assignment.getRawTargets().length == 1 && assignment.getLeftHandSideExpression() == target;
  }

  @SuppressWarnings("unused")
  public static void insertVariableTypeComment(@NotNull PyTargetExpression target,
                                               @NotNull String annotation,
                                               boolean startTemplate) {
    insertVariableTypeComment(target, annotation, startTemplate, Collections.singletonList(TextRange.from(0, annotation.length())));
  }

  public static void insertVariableTypeComment(@NotNull PyTargetExpression target,
                                               @NotNull String annotation,
                                               boolean startTemplate,
                                               @NotNull List<TextRange> typeRanges) {
    final String typeCommentPrefix = "# type: ";
    final String typeCommentText = "  " + typeCommentPrefix + annotation;

    final PyStatement statement = PsiTreeUtil.getParentOfType(target, PyStatement.class);
    final PsiElement insertionAnchor;
    if (statement instanceof PyAssignmentStatement) {
      insertionAnchor = statement.getLastChild();
    }
    else if (statement instanceof PyWithStatement) {
      insertionAnchor = PyUtil.getHeaderEndAnchor((PyStatementListContainer)statement);
    }
    else if (statement instanceof PyForStatement) {
      insertionAnchor = PyUtil.getHeaderEndAnchor(((PyForStatement)statement).getForPart());
    }
    else {
      throw new IllegalArgumentException("Target expression must belong to an assignment, \"with\" statement or \"for\" loop");
    }

    if (insertionAnchor instanceof PsiComment) {
      final String combinedTypeCommentText = typeCommentText + " " + insertionAnchor.getText();
      final PsiElement lastNonComment = PyPsiUtils.getPrevNonCommentSibling(insertionAnchor, true);
      final int startOffset = lastNonComment.getTextRange().getEndOffset();
      final int endOffset = insertionAnchor.getTextRange().getEndOffset();
      PyUtil.updateDocumentUnblockedAndCommitted(target, document -> {
        document.replaceString(startOffset, endOffset, combinedTypeCommentText);
      });
    }
    else if (insertionAnchor != null) {
      final int offset = insertionAnchor.getTextRange().getEndOffset();
      PyUtil.updateDocumentUnblockedAndCommitted(target, document -> {
        document.insertString(offset, typeCommentText);
      });
    }

    final PsiComment insertedComment = target.getTypeComment();
    if (startTemplate && insertedComment != null) {
      final int initialCaretOffset = insertedComment.getTextRange().getStartOffset();
      final VirtualFile updatedVirtualFile = insertedComment.getContainingFile().getVirtualFile();
      final Project project = target.getProject();
      final OpenFileDescriptor descriptor = new OpenFileDescriptor(project, updatedVirtualFile, initialCaretOffset);
      final Editor editor = FileEditorManager.getInstance(project).openTextEditor(descriptor, true);

      if (editor != null) {
        final boolean testMode = ApplicationManager.getApplication().isUnitTestMode();
        editor.getCaretModel().moveToOffset(initialCaretOffset);
        final TemplateBuilder templateBuilder = TemplateBuilderFactory.getInstance().createTemplateBuilder(insertedComment);
        //noinspection ConstantConditions
        for (TextRange range : typeRanges) {
          final String individualType = range.substring(annotation);
          final String replacementText = testMode ? "[" + individualType + "]" : individualType;
          templateBuilder.replaceRange(range.shiftRight(typeCommentPrefix.length()), replacementText);
        }
        templateBuilder.run(editor, true);
      }
    }
  }
}
