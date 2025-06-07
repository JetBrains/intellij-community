package com.jetbrains.python;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.util.NlsContexts.*;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.util.Consumer;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

@ApiStatus.Experimental
public class PythonUiService {

  public void showBalloonInfo(Project project, @PopupContent String message) {}

  public void showBalloonWarning(Project project, @PopupContent String message) {}

  public void showBalloonError(Project project, @PopupContent String message) {}

  public FileEditor getSelectedEditor(@NotNull Project project, VirtualFile virtualFile) {
    return null;
  }

  public Editor openTextEditor(@NotNull Project project, PsiElement anchor) {
    return null;
  }

  public Editor openTextEditor(@NotNull Project project, VirtualFile virtualFile) {
    return null;
  }

  public Editor openTextEditor(@NotNull Project project, VirtualFile virtualFile, int offset) {
    return null;
  }

  public boolean showYesDialog(Project project, @DialogTitle String title, @DialogMessage String message) {
    return false;
  }

  public void runRenameProcessor(Project project,
                                 PsiElement element,
                                 String newName,
                                 boolean searchInComments,
                                 boolean searchTextOccurrences) {}

  public LocalQuickFix createPyChangeSignatureQuickFixForMismatchingMethods(PyFunction function, PyFunction method) {
    return null;
  }

  public LocalQuickFix createPyChangeSignatureQuickFixForMismatchedCall(@NotNull PyCallExpression.PyArgumentsMapping mapping) {
    return null;
  }


  public LocalQuickFix createPyImplementMethodsQuickFix(PyClass aClass, List<PyFunction> toImplement) {
    return null;
  }

  public void annotateTypesIntention(Editor editor, PyFunction function) {
  }

  public void showErrorHint(Editor editor, @NotNull @HintText String message) {
  }

  public static PythonUiService getInstance() {
    return ApplicationManager.getApplication().getService(PythonUiService.class);
  }

  public @Nullable LocalQuickFix createPyRenameElementQuickFix(final @NotNull PsiElement element) {
    return null;
  }

  public void showPopup(Project project, List<String> items, @PopupTitle String title, Consumer<String> callback) {
  }

  /**
   * Shows a panel with name redefinition conflicts, if needed.
   *
   * @param conflicts what {@link #findDefinitions} would return
   * @param obscured  name or its topmost qualifier that is obscured, used at top of pane.
   * @param name      full name (maybe qualified) to show as obscured and display as qualifier in "would be" chunks.
   * @return true iff conflicts is not empty and the panel is shown.
   */
  public boolean showConflicts(Project project,
                               List<? extends Pair<PsiElement, PsiElement>> conflicts,
                               String obscured,
                               @Nullable String name) {
    return false;
  }

  public @Nullable String showInputDialog(@Nullable Project project,
                                          @DialogMessage String message,
                                          @DialogTitle String title,
                                          @Nullable String initialValue,
                                          @Nullable InputValidator validator) {
    return null;
  }


  public int showChooseDialog(@Nullable Project project,
                              @Nullable Component parentComponent,
                              @DialogMessage String message,
                              @DialogTitle String title,
                              String @ListItem [] values,
                              @ListItem String initialValue,
                              @Nullable Icon icon) {
    return -1;

  }
}
