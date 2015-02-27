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
import com.intellij.refactoring.classMembers.MemberInfoBase;
import com.intellij.refactoring.classMembers.MemberInfoChange;
import com.intellij.refactoring.classMembers.MemberInfoModel;
import com.intellij.refactoring.ui.AbstractMemberSelectionTable;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.intellij.ui.RowIcon;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TableUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Mikhail Golubev
 */
public class PyMoveTopLevelSymbolDialog extends RefactoringDialog {

  /**
   * Instance to be injected to mimic this class in tests
   */
  private static PyMoveTopLevelSymbolDialog ourInstanceToReplace = null;

  private final TopLevelSymbolsSelectionTable myMemberSelectionTable;
  private final ModuleMemberInfoModel myModuleMemberModel;
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
  public static PyMoveTopLevelSymbolDialog getInstance(@NotNull final Project project,
                                                       @NotNull final PsiNamedElement[] elements,
                                                       @Nullable final String destination) {
    return ourInstanceToReplace != null ? ourInstanceToReplace : new PyMoveTopLevelSymbolDialog(project, elements, destination);
  }

  /**
   * Injects instance to be used in tests
   *
   * @param instanceToReplace instance to be used in tests
   */
  @TestOnly
  public static void setInstanceToReplace(@NotNull final PyMoveTopLevelSymbolDialog instanceToReplace) {
    ourInstanceToReplace = instanceToReplace;
  }

  /**
   * @param project dialog project
   * @param elements elements to move
   * @param destination destination where elements have to be moved
   */
  protected PyMoveTopLevelSymbolDialog(@NotNull Project project, @NotNull PsiNamedElement[] elements, @Nullable String destination) {
    super(project, true);

    assert elements.length > 0;
    final String moveText;

    final PsiNamedElement firstElement = elements[0];
    if (elements.length == 1) {
      if (firstElement instanceof PyClass) {
        moveText = PyBundle.message("refactoring.move.class.$0", ((PyClass)firstElement).getQualifiedName());
      }
      else {
        moveText = PyBundle.message("refactoring.move.function.$0", firstElement.getName());
      }
    }
    else {
      moveText = PyBundle.message("refactoring.move.selected.elements");
    }
    setTitle(moveText);

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
    myModuleMemberModel = new ModuleMemberInfoModel(pyFile);
    myMemberSelectionTable = new TopLevelSymbolsSelectionTable(myModuleMemberModel.getTopLevelSymbolInfo(), myModuleMemberModel);
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
  public Collection<TopLevelSymbolInfo> getSelectedMembers() {
    return myMemberSelectionTable.getSelectedMemberInfos();
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

  static class ModuleMemberInfoModel implements MemberInfoModel<PyElement, TopLevelSymbolInfo> {
    final PyFile myPyFile;

    public ModuleMemberInfoModel(@NotNull PyFile pyFile) {
      myPyFile = pyFile;
    }

    @NotNull
    public List<PyElement> getTopLevelSymbols() {
      final List<PyElement> result = new ArrayList<PyElement>();
      result.addAll(myPyFile.getTopLevelAttributes());
      result.addAll(myPyFile.getTopLevelClasses());
      result.addAll(myPyFile.getTopLevelFunctions());
      return result;
    }

    @NotNull
    public List<TopLevelSymbolInfo> getTopLevelSymbolInfo() {
      return ContainerUtil.mapNotNull(getTopLevelSymbols(), new Function<PyElement, TopLevelSymbolInfo>() {
        @Override
        public TopLevelSymbolInfo fun(PyElement element) {
          return new TopLevelSymbolInfo(element);
        }
      });
    }

    @Override
    public boolean isMemberEnabled(TopLevelSymbolInfo member) {
      return true;
    }

    @Override
    public boolean isCheckedWhenDisabled(TopLevelSymbolInfo member) {
      return false;
    }

    @Override
    public boolean isAbstractEnabled(TopLevelSymbolInfo member) {
      return false;
    }

    @Override
    public boolean isAbstractWhenDisabled(TopLevelSymbolInfo member) {
      return false;
    }

    @Override
    public Boolean isFixedAbstract(TopLevelSymbolInfo member) {
      return null;
    }

    @Override
    public int checkForProblems(@NotNull TopLevelSymbolInfo member) {
      return 0;
    }

    @Override
    public String getTooltipText(TopLevelSymbolInfo member) {
      return "";
    }

    @Override
    public void memberInfoChanged(MemberInfoChange<PyElement, TopLevelSymbolInfo> event) {

    }
  }

  static class TopLevelSymbolInfo extends MemberInfoBase<PyElement> {
    public TopLevelSymbolInfo(PyElement member) {
      super(member);
    }

    @Override
    public boolean isStatic() {
      return true;
    }

    @Override
    public String getDisplayName() {
      return getMember().getName();
    }
  }

  static class TopLevelSymbolsSelectionTable extends AbstractMemberSelectionTable<PyElement, TopLevelSymbolInfo> {
    public TopLevelSymbolsSelectionTable(@NotNull Collection<TopLevelSymbolInfo> memberInfos,
                                         @Nullable MemberInfoModel<PyElement, TopLevelSymbolInfo> memberInfoModel) {
      super(memberInfos, memberInfoModel, null);
      // TODO: It's better to make AbstractMemberSelectionTable more flexible than to patch model like that
      myTableModel = new MyTableModel<PyElement, TopLevelSymbolInfo>(this) {
        @Override
        public String getColumnName(int column) {
          return column == DISPLAY_NAME_COLUMN ? "Symbol" : super.getColumnName(column);
        }
      };
      final TableCellRenderer oldRenderer = getColumnModel().getColumn(DISPLAY_NAME_COLUMN).getCellRenderer();

      setModel(myTableModel);
      getColumnModel().getColumn(DISPLAY_NAME_COLUMN).setCellRenderer(oldRenderer);
      TableUtil.setupCheckboxColumn(getColumnModel().getColumn(CHECKED_COLUMN));
    }

    @Nullable
    @Override
    protected Object getAbstractColumnValue(TopLevelSymbolInfo memberInfo) {
      return null;
    }

    @Override
    protected boolean isAbstractColumnEditable(int rowIndex) {
      return false;
    }

    @Override
    protected void setVisibilityIcon(TopLevelSymbolInfo memberInfo, RowIcon icon) {

    }

    @Override
    protected Icon getOverrideIcon(TopLevelSymbolInfo memberInfo) {
      return null;
    }
  }
}
