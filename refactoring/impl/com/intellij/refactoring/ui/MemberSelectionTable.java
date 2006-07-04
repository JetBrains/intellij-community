/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 17.06.2002
 * Time: 16:35:43
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.ui;

import com.intellij.ide.IconUtilEx;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.*;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.refactoring.util.classMembers.MemberInfoChange;
import com.intellij.refactoring.util.classMembers.MemberInfoChangeListener;
import com.intellij.refactoring.util.classMembers.MemberInfoModel;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.ui.BooleanTableCellRenderer;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.RowIcon;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.Table;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.util.ArrayList;

public class MemberSelectionTable extends Table {
  private static final int CHECKED_COLUMN = 0;
  private static final int DISPLAY_NAME_COLUMN = 1;
  private static final int ABSTRACT_COLUMN = 2;
  private static final Icon OVERRIDING_METHOD_ICON = IconLoader.getIcon("/general/overridingMethod.png");
  private static final Icon IMPLEMENTING_METHOD_ICON = IconLoader.getIcon("/general/implementingMethod.png");
  private static final Icon EMPTY_OVERRIDE_ICON = new EmptyIcon(16, 16);

  private final String myAbstractColumnHeader;
  private static final String DISPLAY_NAME_COLUMN_HEADER = RefactoringBundle.message("member.column");

  private MemberInfo[] myMemberInfos;
  private final boolean myAbstractEnabled;
  private MemberInfoModel myMemberInfoModel;
  private MyTableModel myTableModel;


  private static class DefaultMemberInfoModel implements MemberInfoModel {
    public boolean isMemberEnabled(MemberInfo member) {
      return true;
    }

    public boolean isCheckedWhenDisabled(MemberInfo member) {
      return false;
    }

    public boolean isAbstractEnabled(MemberInfo member) {
      return true;
    }

    public boolean isAbstractWhenDisabled(MemberInfo member) {
      return false;
    }


    public int checkForProblems(MemberInfo member) {
      return OK;
    }

    public void memberInfoChanged(MemberInfoChange event) {
    }

    public Boolean isFixedAbstract(MemberInfo member) {
      return null;
    }

    public String getTooltipText(MemberInfo member) {
      return null;
    }
  }

  private static final DefaultMemberInfoModel defaultMemberInfoModel = new DefaultMemberInfoModel();

  public MemberSelectionTable(final MemberInfo[] memberInfos, String abstractColumnHeader) {
    this(memberInfos, null, abstractColumnHeader);
  }

  public MemberSelectionTable(final MemberInfo[] memberInfos,
                              MemberInfoModel memberInfoModel,
                              String abstractColumnHeader) {
    super();
    myAbstractEnabled = abstractColumnHeader != null;
    myAbstractColumnHeader = abstractColumnHeader;
    myTableModel = new MyTableModel();

    myMemberInfos = memberInfos;
    if (memberInfoModel != null) {
      myMemberInfoModel = memberInfoModel;
    }
    else {
      myMemberInfoModel = defaultMemberInfoModel;
    }

    setModel(myTableModel);

//    myTable.setTableHeader(null);
//    this.setDefaultRenderer(Boolean.class, new MyBooleanRenderer());
    TableColumnModel model = getColumnModel();
    model.getColumn(DISPLAY_NAME_COLUMN).setCellRenderer(new MyTableRenderer());
    model.getColumn(CHECKED_COLUMN).setCellRenderer(new MyBooleanRenderer());
    final int checkBoxWidth = new JCheckBox().getPreferredSize().width;
    model.getColumn(CHECKED_COLUMN).setMaxWidth(checkBoxWidth);
    model.getColumn(CHECKED_COLUMN).setMinWidth(checkBoxWidth);

    if (myAbstractEnabled) {
      int width =
        (int)(1.3 * getFontMetrics(getFont()).charsWidth(myAbstractColumnHeader.toCharArray(), 0,
                                                             myAbstractColumnHeader.length()));
      model.getColumn(ABSTRACT_COLUMN).setMaxWidth(width);
      model.getColumn(ABSTRACT_COLUMN).setPreferredWidth(width);
      model.getColumn(ABSTRACT_COLUMN).setCellRenderer(new MyBooleanRenderer());
    }

    setPreferredScrollableViewportSize(new Dimension(400, getRowHeight() * 12));
    getSelectionModel().setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    setShowGrid(false);
    setIntercellSpacing(new Dimension(0, 0));

    new MyEnableDisableAction().register();
  }

  public MemberInfo[] getSelectedMemberInfos() {
    ArrayList<MemberInfo> list = new ArrayList<MemberInfo>(myMemberInfos.length);
    for (MemberInfo info : myMemberInfos) {
      final boolean memberEnabled = myMemberInfoModel.isMemberEnabled(info);
      if ((memberEnabled && info.isChecked()) || (!memberEnabled && myMemberInfoModel.isCheckedWhenDisabled(info))) {
//      if (info.isChecked() || (!myMemberInfoModel.isMemberEnabled(info) && myMemberInfoModel.isCheckedWhenDisabled(info))) {
        list.add(info);
      }
    }
    return list.toArray(new MemberInfo[list.size()]);
  }

  public MemberInfoModel getMemberInfoModel() {
    return myMemberInfoModel;
  }

  public void setMemberInfoModel(MemberInfoModel memberInfoModel) {
    myMemberInfoModel = memberInfoModel;
  }

  public void fireExternalDataChange() {
    myTableModel.fireTableDataChanged();
  }

  public void setMemberInfos(MemberInfo[] memberInfos) {
    myMemberInfos = memberInfos;
    fireMemberInfoChange(memberInfos);
    myTableModel.fireTableDataChanged();
  }

  public void addMemberInfoChangeListener(MemberInfoChangeListener l) {
    listenerList.add(MemberInfoChangeListener.class, l);
  }

  protected void fireMemberInfoChange(MemberInfo[] changedMembers) {
    Object[] list = listenerList.getListenerList();

    MemberInfoChange event = new MemberInfoChange(changedMembers);
    for (Object element : list) {
      if (element instanceof MemberInfoChangeListener) {
        ((MemberInfoChangeListener)element).memberInfoChanged(event);
      }
    }
  }

  private class MyTableModel extends AbstractTableModel {
    public int getColumnCount() {
      if (myAbstractEnabled) {
        return 3;
      }
      else {
        return 2;
      }
    }

    public int getRowCount() {
      return myMemberInfos.length;
    }

    public Class getColumnClass(int columnIndex) {
      if (columnIndex == MemberSelectionTable.CHECKED_COLUMN || columnIndex == MemberSelectionTable.ABSTRACT_COLUMN) {
        return Boolean.class;
      }
      return super.getColumnClass(columnIndex);
    }

    public Object getValueAt(int rowIndex, int columnIndex) {
      final MemberInfo memberInfo = myMemberInfos[rowIndex];
      switch (columnIndex) {
        case MemberSelectionTable.CHECKED_COLUMN:
          if (myMemberInfoModel.isMemberEnabled(memberInfo)) {
            return memberInfo.isChecked() ? Boolean.TRUE : Boolean.FALSE;
          }
          else {
            return myMemberInfoModel.isCheckedWhenDisabled(memberInfo);
          }
        case MemberSelectionTable.ABSTRACT_COLUMN:
          {
            if (!(memberInfo.getMember() instanceof PsiMethod)) return null;
            if (memberInfo.isStatic()) return null;

            PsiMethod method = (PsiMethod)memberInfo.getMember();
            if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
              final Boolean fixedAbstract = myMemberInfoModel.isFixedAbstract(memberInfo);
              if (fixedAbstract != null) return fixedAbstract;
            }

            if (!myMemberInfoModel.isAbstractEnabled(memberInfo)) {
              return myMemberInfoModel.isAbstractWhenDisabled(memberInfo);
            }
            else {
              return memberInfo.isToAbstract() ? Boolean.TRUE : Boolean.FALSE;
            }
          }
        case MemberSelectionTable.DISPLAY_NAME_COLUMN:
          return memberInfo.getDisplayName();
        default:
          throw new RuntimeException("Incorrect column index");
      }
    }

    public String getColumnName(int column) {
      switch (column) {
        case MemberSelectionTable.CHECKED_COLUMN:
          return " ";
        case MemberSelectionTable.ABSTRACT_COLUMN:
          return myAbstractColumnHeader;
        case MemberSelectionTable.DISPLAY_NAME_COLUMN:
          return MemberSelectionTable.DISPLAY_NAME_COLUMN_HEADER;
        default:
          throw new RuntimeException("Incorrect column index");
      }
    }

    public boolean isCellEditable(int rowIndex, int columnIndex) {
      switch (columnIndex) {
        case MemberSelectionTable.CHECKED_COLUMN:
          return myMemberInfoModel.isMemberEnabled(myMemberInfos[rowIndex]);
        case MemberSelectionTable.ABSTRACT_COLUMN:
          {
            MemberInfo info = myMemberInfos[rowIndex];
            if (!(info.getMember() instanceof PsiMethod)) return false;

            PsiMethod method = (PsiMethod)info.getMember();
            if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
              if (myMemberInfoModel.isFixedAbstract(info) != null) {
                return false;
              }
            }

            return info.isChecked() && myMemberInfoModel.isAbstractEnabled(info);
          }
      }
      return false;
    }

    public void setValueAt(final Object aValue, final int rowIndex, final int columnIndex) {
      if (columnIndex == MemberSelectionTable.CHECKED_COLUMN) {
        myMemberInfos[rowIndex].setChecked(((Boolean)aValue).booleanValue());
      }
      else if (columnIndex == MemberSelectionTable.ABSTRACT_COLUMN) {
        myMemberInfos[rowIndex].setToAbstract(((Boolean)aValue).booleanValue());
      }

      MemberInfo[] changed = {myMemberInfos[rowIndex]};
      fireMemberInfoChange(changed);
      fireTableDataChanged();
//      fireTableRowsUpdated(rowIndex, rowIndex);
    }
  }

  private class MyTableRenderer extends ColoredTableCellRenderer {
    public void customizeCellRenderer(JTable table, final Object value,
                                      boolean isSelected, boolean hasFocus, final int row, final int column) {

      final int modelColumn = convertColumnIndexToModel(column);
      final MemberInfo memberInfo = myMemberInfos[row];
      setToolTipText(myMemberInfoModel.getTooltipText(memberInfo));
      PsiElement member = memberInfo.getMember();
      switch (modelColumn) {
        case MemberSelectionTable.DISPLAY_NAME_COLUMN:
          {
            Icon memberIcon = member.getIcon(0);
            Icon overrideIcon = MemberSelectionTable.EMPTY_OVERRIDE_ICON;
            if (member instanceof PsiMethod) {
              if (Boolean.TRUE.equals(memberInfo.getOverrides())) {
                overrideIcon = MemberSelectionTable.OVERRIDING_METHOD_ICON;
              }
              else if (Boolean.FALSE.equals(memberInfo.getOverrides())) {
                overrideIcon = MemberSelectionTable.IMPLEMENTING_METHOD_ICON;
              }
              else {
                overrideIcon = MemberSelectionTable.EMPTY_OVERRIDE_ICON;
              }
            }

            RowIcon icon = new RowIcon(3);
            icon.setIcon(memberIcon, 0);
            PsiModifierList modifiers = member instanceof PsiModifierListOwner ? ((PsiModifierListOwner)member).getModifierList() : null;
            if (modifiers != null) {
              IconUtilEx.setVisibilityIcon(modifiers, icon);
            }
            else {
              icon.setIcon(IconUtilEx.getEmptyIcon(true), 1);
            }
            icon.setIcon(overrideIcon, 2);
            setIcon(icon);
            break;
          }
        default:
          {
            setIcon(null);
          }
      }
      final boolean cellEditable = myMemberInfoModel.isMemberEnabled(memberInfo);
      setEnabled(cellEditable);

      if (value == null) return;
      final int problem = myMemberInfoModel.checkForProblems(memberInfo);
      Color c = null;
      if (problem == MemberInfoModel.ERROR) {
        c = Color.red;
      }
      else if (problem == MemberInfoModel.WARNING && !isSelected) {
        c = Color.blue;
      }
      append((String)value, new SimpleTextAttributes(Font.PLAIN, c));
    }
  }

  private class MyBooleanRenderer extends BooleanTableCellRenderer {
    public Component getTableCellRendererComponent(JTable table, Object value,
                                                   boolean isSelected, boolean hasFocus,
                                                   int row, int column) {
      Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      if (component instanceof JComponent) {
        JComponent jComponent = (JComponent)component;
        int modelColumn = convertColumnIndexToModel(column);
        final MemberInfo memberInfo = myMemberInfos[row];
        boolean fixedAbstract = false;
        final PsiElement member = memberInfo.getMember();
        if (modelColumn == ABSTRACT_COLUMN && member instanceof PsiMethod
            && ((PsiMethod)member).hasModifierProperty(PsiModifier.ABSTRACT)) {
          fixedAbstract = myMemberInfoModel.isFixedAbstract(memberInfo) != null;
        }

        jComponent.setEnabled(
          (modelColumn == MemberSelectionTable.CHECKED_COLUMN
           && myMemberInfoModel.isMemberEnabled(memberInfo)
           || (modelColumn == ABSTRACT_COLUMN
               && memberInfo.isChecked()
               && memberInfo.getMember() instanceof PsiMethod
               && !fixedAbstract
               && myMemberInfoModel.isAbstractEnabled(memberInfo)))
        );
      }
      return component;
    }
  }

  private class MyEnableDisableAction extends EnableDisableAction {

    protected JTable getTable() {
      return MemberSelectionTable.this;
    }

    protected void applyValue(int[] rows, boolean valueToBeSet) {
      MemberInfo[] changedInfo = new MemberInfo[rows.length];
      for (int idx = 0; idx < rows.length; idx++) {
        final MemberInfo memberInfo = myMemberInfos[rows[idx]];
        memberInfo.setChecked(valueToBeSet);
        changedInfo[idx] = memberInfo;
      }
      fireMemberInfoChange(changedInfo);
      final int selectedRow = getSelectedRow();
      myTableModel.fireTableDataChanged();
      setRowSelectionInterval(selectedRow, selectedRow);
    }

    protected boolean isRowChecked(final int row) {
      return myMemberInfos[row].isChecked();
    }
  }
}
