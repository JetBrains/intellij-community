package com.intellij.tasks.generic;

import com.intellij.execution.util.ListTableWithButtons;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ui.AbstractTableCellEditor;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.List;

/**
 * User: evgeny.zakrevsky
 * Date: 10/26/12
 */
public class ManageTemplateVariablesDialog extends DialogWrapper {
  private final TemplateVariablesTable myTemplateVariableTable;

  protected ManageTemplateVariablesDialog(@NotNull final Component parent) {
    super(parent, true);
    myTemplateVariableTable = new TemplateVariablesTable();
    setTitle("Template Variables");
    init();
  }

  public void setTemplateVariables(List<TemplateVariable> list) {
    myTemplateVariableTable.setValues(list);
  }

  public List<TemplateVariable> getTemplateVariables() {
    return myTemplateVariableTable.getTemplateVariables();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myTemplateVariableTable.getComponent();
  }

  private static class TemplateVariablesTable extends ListTableWithButtons<TemplateVariable> {
    public TemplateVariablesTable() {
      getTableView().getEmptyText().setText("No variables");
    }

    @Override
    protected ListTableModel createListModel() {
      final ColumnInfo name = new ElementsColumnInfoBase<TemplateVariable>("Name") {
        @Nullable
        @Override
        protected String getDescription(final TemplateVariable templateVariable) {
          return templateVariable.getDescription();
        }

        @Nullable
        @Override
        public String valueOf(final TemplateVariable templateVariable) {
          return templateVariable.getName();
        }

        @Override
        public boolean isCellEditable(TemplateVariable templateVariable) {
          return !templateVariable.isReadOnly();
        }

        @Override
        public void setValue(TemplateVariable templateVariable, String s) {
          if (s.equals(valueOf(templateVariable))) {
            return;
          }
          templateVariable.setName(s);
          setModified();
        }
      };

      final ColumnInfo value = new ElementsColumnInfoBase<TemplateVariable>("Value") {
        @Nullable
        @Override
        public String valueOf(TemplateVariable templateVariable) {
          return templateVariable.getValue();
        }

        @Override
        public boolean isCellEditable(TemplateVariable templateVariable) {
          return !templateVariable.isReadOnly();
        }

        @Override
        public void setValue(TemplateVariable templateVariable, String s) {
          templateVariable.setValue(s);
          setModified();
        }

        @Override
        public TableCellRenderer getRenderer(TemplateVariable variable) {
          if (variable.isHidden()) {
            return new TableCellRenderer() {
              @Override
              public Component getTableCellRendererComponent(JTable table,
                                                             Object value,
                                                             boolean isSelected,
                                                             boolean hasFocus,
                                                             int row,
                                                             int column) {
                return new JPasswordField(value.toString());
              }
            };
          }
          return super.getRenderer(variable);
        }

        @Nullable
        @Override
        public TableCellEditor getEditor(final TemplateVariable variable) {
          if (variable.isHidden()) {
            return new AbstractTableCellEditor() {
              private JPasswordField myPasswordField;
              @Override
              public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
                myPasswordField = new JPasswordField(variable.getValue());
                return myPasswordField;
              }

              @Override
              public Object getCellEditorValue() {
                return myPasswordField.getText();
              }
            };
          }
          return super.getEditor(variable);
        }

        @Nullable
        @Override
        protected String getDescription(TemplateVariable templateVariable) {
          return templateVariable.getDescription();
        }
      };

      final ColumnInfo isShownOnFirstTab = new ColumnInfo<TemplateVariable, Boolean>("Show on first tab") {
        @Nullable
        @Override
        public Boolean valueOf(TemplateVariable o) {
          return o.isShownOnFirstTab();
        }

        @Override
        public void setValue(TemplateVariable variable, Boolean value) {
          variable.setShownOnFirstTab(value);
          setModified();
        }

        @Override
        public Class getColumnClass() {
          return Boolean.class;
        }

        @Override
        public boolean isCellEditable(TemplateVariable variable) {
          return !variable.isReadOnly();
        }

        @Nullable
        @Override
        public String getTooltipText() {
          return "Whether this template variable will be shown in 'General tab'";
        }
      };

      final ColumnInfo isHidden = new ColumnInfo<TemplateVariable, Boolean>("Hide") {
        @Nullable
        @Override
        public Boolean valueOf(TemplateVariable o) {
          return o.isHidden();
        }

        @Override
        public void setValue(TemplateVariable variable, Boolean value) {
          variable.setHidden(value);
          setModified();
          // value column editor may be changed
          TemplateVariablesTable.this.refreshValues();
        }

        @Override
        public Class getColumnClass() {
          return Boolean.class;
        }

        @Override
        public boolean isCellEditable(TemplateVariable variable) {
          return !variable.isReadOnly();
        }

        @Nullable
        @Override
        public String getTooltipText() {
          return "Whether this template variable will be hidden like password field";
        }
      };
      return new ListTableModel((new ColumnInfo[]{name, value, isShownOnFirstTab, isHidden}));
    }

    @Override
    protected TemplateVariable createElement() {
      return new TemplateVariable("", "");
    }

    @Override
    protected boolean isEmpty(TemplateVariable element) {
      return StringUtil.isEmpty(element.getName()) && StringUtil.isEmpty(element.getValue());
    }

    @Override
    protected TemplateVariable cloneElement(final TemplateVariable variable) {
      return variable.clone();
    }

    @Override
    protected boolean canDeleteElement(final TemplateVariable selection) {
      return true;
    }

    public List<TemplateVariable> getTemplateVariables() {
      return getElements();
    }
  }
}
