package com.jetbrains.python.refactoring.move;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyClass;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author vlan
 */
public class PyMoveClassOrFunctionDialog extends RefactoringDialog {
  private PyMoveClassOrFunctionPanel myPanel;

  public PyMoveClassOrFunctionDialog(@NotNull Project project, PsiNamedElement[] elements) {
    super(project, true);
    assert elements.length > 0;
    final String moveText;
    if (elements.length == 1) {
      PsiNamedElement e = elements[0];
      if (e instanceof PyClass) {
        moveText = PyBundle.message("refactoring.move.class.$0", ((PyClass)e).getQualifiedName());
      }
      else {
        moveText = PyBundle.message("refactoring.move.function.$0", e.getName());
      }
    }
    else {
      moveText = PyBundle.message("refactoring.move.selected.elements");
    }

    myPanel = new PyMoveClassOrFunctionPanel(moveText, getContainingFileName(elements[0]));
    setTitle(PyBundle.message("refactoring.move.class.or.function.dialog.title"));

    final FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, false, false, false, false);
    for (VirtualFile file : ProjectRootManager.getInstance(project).getContentRoots()) {
      descriptor.addRoot(file);
    }
    descriptor.setIsTreeRootVisible(true);

    myPanel.getBrowseTargetFileButton().addBrowseFolderListener(PyBundle.message("refactoring.move.class.or.function.choose.destination.file.title"),
                                                                null, project, descriptor,
                                                                TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT);
    init();
  }

  public String getTargetPath() {
    return myPanel.getBrowseTargetFileButton().getText();
  }

  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Override
  protected void doAction() {
    close(OK_EXIT_CODE);
  }

  @Override
  protected String getHelpId() {
    return "refactoring.moveClass";
  }

  private static String getContainingFileName(PsiElement element) {
    VirtualFile file = element.getContainingFile().getVirtualFile();
    if (file != null) {
      return FileUtil.toSystemDependentName(file.getPath());
    }
    else {
      return "";
    }
  }
}
