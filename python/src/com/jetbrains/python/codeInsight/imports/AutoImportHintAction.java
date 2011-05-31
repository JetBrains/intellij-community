package com.jetbrains.python.codeInsight.imports;

import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInspection.HintAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class AutoImportHintAction implements LocalQuickFix, HintAction, HighPriorityAction {
  private final AutoImportQuickFix myDelegate;

  public AutoImportHintAction(AutoImportQuickFix delegate) {
    myDelegate = delegate;
  }

  @Override
  public boolean showHint(Editor editor) {
    return myDelegate.showHint(editor);
  }

  @NotNull
  @Override
  public String getText() {
    return myDelegate.getText();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return myDelegate.isAvailable();
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    myDelegate.invoke(file);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @NotNull
  @Override
  public String getName() {
    return myDelegate.getName();
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return myDelegate.getFamilyName();
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    myDelegate.applyFix(project, descriptor);
  }
}
