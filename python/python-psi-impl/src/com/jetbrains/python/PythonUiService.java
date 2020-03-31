package com.jetbrains.python;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.JDOMExternalizableStringList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.Consumer;
import com.jetbrains.python.inspections.PyMandatoryEncodingInspection;
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

  public void showBalloonInfo(Project project, String message) {}

  public void showBalloonError(Project project, String message) {}

  public Editor openTextEditor(@NotNull Project project, VirtualFile virtualFile) {
    return null;
  }

  public boolean showYesDialog(Project project, String title, String message) {
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

  public JComponent createSingleCheckboxOptionsPanel(String label, InspectionProfileEntry inspection, String property) {
    return null;
  }

  public void annotateTypesIntention(Editor editor, PyFunction function) {
  }

  @NotNull
  public JComponent createEncodingsOptionsPanel(String[] possibleEncodings,
                                                String defaultEncoding,
                                                String[] possibleFormats,
                                                int formatIndex,
                                                Consumer<String> encodingChanged,
                                                Consumer<Integer> formatIndexChanged) {
    return null;
  }

  public JCheckBox createInspectionCheckBox(String message, InspectionProfileEntry inspection, String property) {
    return null;
  }

  public <E> JComboBox<E> createComboBox(E[] items) {
    return null;
  }

  public <E> JComboBox<E> createComboBox(E[] items, int width) {
    return null;
  }

  public JComponent createListEditForm(String title, List<String> stringList) {
    return null;
  }

  public JComponent onePixelSplitter(boolean b, JComponent first, JComponent second) {
    return null;
  }

  public static PythonUiService getInstance() {
    return ServiceManager.getService(PythonUiService.class);
  }

  @Nullable
  public LocalQuickFix createPyRenameElementQuickFix(@NotNull final PsiElement element) {
    return null;
  }

  @Nullable
  public JComponent createComboBoxWithLabel(@NotNull String label,
                                            String[] items,
                                            final String selectedItem,
                                            Consumer<Object> selectedItemChanged) {
    return null;
  }

  public void showPopup(Project project, List<String> items, String title, Consumer<String> callback) {
  }
}
