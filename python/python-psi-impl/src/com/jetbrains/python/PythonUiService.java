// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.util.NlsContexts.DialogMessage;
import com.intellij.openapi.util.NlsContexts.DialogTitle;
import com.intellij.openapi.util.NlsContexts.HintText;
import com.intellij.openapi.util.NlsContexts.ListItem;
import com.intellij.openapi.util.NlsContexts.PopupContent;
import com.intellij.openapi.util.NlsContexts.PopupTitle;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.util.Consumer;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.awt.Component;
import java.util.List;

@ApiStatus.Experimental
public class PythonUiService {

  public void showBalloonInfo(Project project, @PopupContent String message) { }

  public void showBalloonWarning(Project project, @PopupContent String message) { }

  public void showBalloonError(Project project, @PopupContent String message) { }

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
                                 boolean searchTextOccurrences) { }

  /**
   * Starts an interactive in-place rename template on {@code element} in the given editor,
   * highlighting the name and updating its usages live as the user types.
   *
   * @param onTemplateFinished reports the end of the template to the caller
   * @return {@code true} if the template was started, {@code false} if in-place rename is unavailable
   * (e.g. the UI layer is missing, the editor cannot host a template, or in-place rename did not start);
   * callers should fall back to a non-interactive flow in that case.
   */
  public boolean invokeInplaceRename(@NotNull Editor editor, @NotNull PsiNameIdentifierOwner element,
                                     @NotNull InplaceRenameListener onTemplateFinished) {
    return false;
  }

  /** Reports the end of the in-place rename template that {@link #invokeInplaceRename} started. */
  @FunctionalInterface
  public interface InplaceRenameListener {
    /**
     * Runs once the rename is over, and after the element has its final name. A caller that inserted the
     * element to rename has to take it out again when the element kept the name it started with.
     *
     * @param brokenOff {@code true} when the template ended without a name: Escape, an invalid name, or a cancelled template
     * @param named     {@code true} when the user gave the element a name other than the one it started with
     */
    void templateFinished(boolean brokenOff, boolean named);
  }

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

  public @Nullable LocalQuickFix createInterpreterSettingsQuickFix(@Nullable Module module) {
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
