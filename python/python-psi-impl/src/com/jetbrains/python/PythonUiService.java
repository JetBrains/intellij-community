package com.jetbrains.python;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.util.JDOMExternalizableStringList;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsContexts.Checkbox;
import com.intellij.openapi.util.NlsContexts.*;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.util.Consumer;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
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

  public JComponent createCompatibilityInspectionOptionsPanel(@NotNull List<String> supportedInSettings,
                                                              JDOMExternalizableStringList ourVersions) {
    return null;
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

  public JComponent createSingleCheckboxOptionsPanel(@Checkbox String label, InspectionProfileEntry inspection, String property) {
    return null;
  }

  public void annotateTypesIntention(Editor editor, PyFunction function) {
  }

  @NotNull
  public JComponent createEncodingsOptionsPanel(String @ListItem [] possibleEncodings,
                                                @ListItem String defaultEncoding,
                                                String @ListItem[] possibleFormats,
                                                int formatIndex,
                                                Consumer<String> encodingChanged,
                                                Consumer<Integer> formatIndexChanged) {
    return null;
  }

  public JCheckBox createInspectionCheckBox(@Checkbox String message, InspectionProfileEntry inspection, String property) {
    return null;
  }

  public <E> JComboBox<E> createComboBox(E[] items) {
    return null;
  }

  public <E> JComboBox<E> createComboBox(E[] items, int width) {
    return null;
  }

  public JComponent createListEditForm(@ColumnName String title, List<String> stringList) {
    return null;
  }

  public JComponent onePixelSplitter(boolean b, JComponent first, JComponent second) {
    return null;
  }

  public void showErrorHint(Editor editor, @NotNull @HintText String message) {
  }

  public static PythonUiService getInstance() {
    return ApplicationManager.getApplication().getService(PythonUiService.class);
  }

  @Nullable
  public LocalQuickFix createPyRenameElementQuickFix(@NotNull final PsiElement element) {
    return null;
  }

  @Nullable
  public JComponent createComboBoxWithLabel(@NotNull @NlsContexts.Label String label,
                                            String @ListItem [] items,
                                            @ListItem String selectedItem,
                                            Consumer<Object> selectedItemChanged) {
    return null;
  }

  public void showPopup(Project project, List<String> items, @PopupTitle String title, Consumer<String> callback) {
  }

  /**
   * Shows a panel with name redefinition conflicts, if needed.
   *
   * @param project
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

  @Nullable
  public String showInputDialog(@Nullable Project project,
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

  public JPanel createMultipleCheckboxOptionsPanel(final InspectionProfileEntry owner) {
    return null;
  }

  public void addRowToOptionsPanel(JPanel optionsPanel, JComponent label, JComponent component) {
  }

  public void addCheckboxToOptionsPanel(JPanel optionsPanel, @NlsContexts.Checkbox String label, @NonNls String property) {
  }
}
