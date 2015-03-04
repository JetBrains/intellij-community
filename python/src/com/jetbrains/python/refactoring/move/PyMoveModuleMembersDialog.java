package com.jetbrains.python.refactoring.move;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.refactoring.classMembers.MemberInfoChange;
import com.intellij.refactoring.classMembers.MemberInfoModel;
import com.intellij.refactoring.ui.AbstractMemberSelectionTable;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.intellij.ui.RowIcon;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.List;

/**
 * @author Mikhail Golubev
 */
public class PyMoveModuleMembersDialog extends RefactoringDialog {

  /**
   * Instance to be injected to mimic this class in tests
   */
  private static PyMoveModuleMembersDialog ourInstanceToReplace = null;

  private final TopLevelSymbolsSelectionTable myMemberSelectionTable;
  private final PyModuleMemberInfoModel myModuleMemberModel;
  private JPanel myCenterPanel;
  private JPanel myTablePanel;
  private TextFieldWithBrowseButton myBrowseFieldWithButton;

  /**
   * Either creates new dialog or return singleton instance initialized with {@link #setInstanceToReplace)}.
   * Singleton dialog is intended to be used in tests.
   *
   * @param project dialog project
   * @param elements elements to move
   * @param destination destination where elements have to be moved
   * @return dialog
   */
  public static PyMoveModuleMembersDialog getInstance(@NotNull final Project project,
                                                       @NotNull final List<PsiNamedElement> elements,
                                                       @Nullable final String destination) {
    return ourInstanceToReplace != null ? ourInstanceToReplace : new PyMoveModuleMembersDialog(project, elements, destination);
  }

  /**
   * Injects instance to be used in tests
   *
   * @param instanceToReplace instance to be used in tests
   */
  @TestOnly
  public static void setInstanceToReplace(@NotNull final PyMoveModuleMembersDialog instanceToReplace) {
    ourInstanceToReplace = instanceToReplace;
  }

  /**
   * @param project dialog project
   * @param elements elements to move
   * @param destination destination where elements have to be moved
   */
  protected PyMoveModuleMembersDialog(@NotNull Project project, @NotNull List<PsiNamedElement> elements, @Nullable String destination) {
    super(project, true);

    assert !elements.isEmpty();
    final PsiNamedElement firstElement = elements.get(0);
    setTitle(PyBundle.message("refactoring.move.module.members.dialog.title"));

    if (destination == null) {
      destination = getContainingFileName(firstElement);
    }
    myBrowseFieldWithButton.setText(destination);
    final FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor();
    descriptor.setRoots(ProjectRootManager.getInstance(project).getContentRoots());
    descriptor.withTreeRootVisible(true);
    myBrowseFieldWithButton.addBrowseFolderListener(PyBundle.message("refactoring.move.class.or.function.choose.destination.file.title"),
                                                    null,
                                                    project,
                                                    descriptor,
                                                    TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT);

    final PyFile pyFile = (PyFile)firstElement.getContainingFile();
    myModuleMemberModel = new PyModuleMemberInfoModel(pyFile);

    final List<PyModuleMemberInfo> symbolsInfos = myModuleMemberModel.collectTopLevelSymbolsInfo();
    for (PyModuleMemberInfo info : symbolsInfos) {
      info.setChecked(elements.contains(info.getMember()));
    }
    myModuleMemberModel.memberInfoChanged(new MemberInfoChange<PyElement, PyModuleMemberInfo>(symbolsInfos));
    myMemberSelectionTable = new TopLevelSymbolsSelectionTable(symbolsInfos, myModuleMemberModel);
    myMemberSelectionTable.addMemberInfoChangeListener(myModuleMemberModel);
    // MoveMemberDialog for Java uses SeparatorFactory.createSeparator instead of custom border
    myTablePanel.add(ScrollPaneFactory.createScrollPane(myMemberSelectionTable), BorderLayout.CENTER);

    init();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myCenterPanel;
  }

  @Override
  protected void doAction() {
    close(OK_EXIT_CODE);
  }

  @Override
  protected String getHelpId() {
    return "python.reference.moveClass";
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myBrowseFieldWithButton.getTextField();
  }

  @NotNull
  public String getTargetPath() {
    return myBrowseFieldWithButton.getText();
  }

  @NotNull
  public List<PyElement> getSelectedTopLevelSymbols() {
    return ContainerUtil.map(myMemberSelectionTable.getSelectedMemberInfos(), new Function<PyModuleMemberInfo, PyElement>() {
      @Override
      public PyElement fun(PyModuleMemberInfo info) {
        return info.getMember();
      }
    });
  }

  @NotNull
  private static String getContainingFileName(@NotNull PsiElement element) {
    final VirtualFile file = element.getContainingFile().getVirtualFile();
    if (file != null) {
      return FileUtil.toSystemDependentName(file.getPath());
    }
    else {
      return "";
    }
  }

  static class TopLevelSymbolsSelectionTable extends AbstractMemberSelectionTable<PyElement, PyModuleMemberInfo> {
    public TopLevelSymbolsSelectionTable(Collection<PyModuleMemberInfo> memberInfos,
                                         @Nullable MemberInfoModel<PyElement, PyModuleMemberInfo> memberInfoModel) {
      super(memberInfos, memberInfoModel, null);
    }

    @Nullable
    @Override
    protected Object getAbstractColumnValue(PyModuleMemberInfo memberInfo) {
      return null;
    }

    @Override
    protected boolean isAbstractColumnEditable(int rowIndex) {
      return false;
    }

    @Override
    protected void setVisibilityIcon(PyModuleMemberInfo memberInfo, RowIcon icon) {

    }

    @Override
    protected Icon getOverrideIcon(PyModuleMemberInfo memberInfo) {
      return null;
    }
  }
}
