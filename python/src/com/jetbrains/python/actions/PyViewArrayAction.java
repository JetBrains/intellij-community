/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.intellij.xdebugger.frame.XFullValueEvaluator;
import com.intellij.xdebugger.impl.ui.tree.SetValueInplaceEditor;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeInplaceEditor;
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author amarch
 */

public class PyViewArrayAction extends XDebuggerTreeActionBase {

  @Override
  protected void perform(XValueNodeImpl node, @NotNull String nodeName, AnActionEvent e) {
    final MyDialog dialog = new MyDialog(e.getProject(), node, nodeName);
    dialog.setTitle("View Array");
    dialog.setValue(node);
    dialog.show();
  }

  private abstract class ArrayValueProvider {

    public abstract Object[][] parseValues(String rawValue);
  }

  private class NumpyArrayValueProvider extends ArrayValueProvider {
    private static final String NUMERIC_VALUE_SEPARATOR = " ";
    private static final String START_ROW_SEPARATOR = "[";
    private final Pattern STR_VALUE_SEPARATOR = Pattern.compile("'.*?[^\\\\]'|\".*?[^\\\\]\"");
    private static final String END_ROW_SEPARATOR = "]";

    @Override
    public Object[][] parseValues(String rawValues) {

      if (rawValues != null && rawValues.startsWith(START_ROW_SEPARATOR)) {
        int dimension = 0;
        while (rawValues.charAt(dimension) == START_ROW_SEPARATOR.charAt(0)) {
          dimension += 1;
        }

        String showedValues =
          dimension > 2 ? rawValues.substring(dimension - 2, rawValues.indexOf(END_ROW_SEPARATOR + END_ROW_SEPARATOR) + 2) : rawValues;
        String[] rows = showedValues.split("\\]\n(\\ )*\\[");

        Object[][] data = null;
        boolean numeric = isNumeric(showedValues);
        for (int i = 0; i < rows.length; i++) {
          Object[] row;

          if (numeric) {
            String clearedRow = rows[i].replace(START_ROW_SEPARATOR, "").replace(END_ROW_SEPARATOR, "").replace("  ", " ").trim();
            row = clearedRow.split(NUMERIC_VALUE_SEPARATOR);
          }
          else {
            String clearedRow = rows[i].replace(START_ROW_SEPARATOR, "").replace(END_ROW_SEPARATOR, "").replace("  ", " ").trim();
            Matcher matcher = STR_VALUE_SEPARATOR.matcher(clearedRow);
            List<String> matches = new ArrayList<String>();
            while (matcher.find()) {
              matches.add(matcher.group());
            }
            row = matches.toArray();
          }

          data = data == null ? new String[rows.length][row.length] : data;
          for (int j = 0; j < row.length; j++) {
            data[i][j] = row[j];
          }
        }
        return data;
      }

      return null;
    }

    private boolean isNumeric(String value) {
      if (value.contains("\'") || value.contains("\"")) {
        return false;
      }
      return true;
    }
  }

  private class MyDialog extends DialogWrapper {
    public JTable myTable;
    private XDebuggerTreeInplaceEditor myEditor;

    private MyComponent myComponent;

    private MyDialog(Project project, XValueNodeImpl node, @NotNull String nodeName) {
      super(project, false);
      setModal(false);
      setCancelButtonText("Close");
      setCrossClosesWindow(true);

      myEditor = new SetValueInplaceEditor(node, nodeName);

      myComponent = new MyComponent();
      myTable = myComponent.getTable();

      init();
    }

    public void setValue(XValueNodeImpl node) {
      ArrayValueProvider valueProvider;

      if (node.getValuePresentation() != null &&
          node.getValuePresentation().getType() != null &&
          node.getValuePresentation().getType().equals("ndarray")) {
        valueProvider = new NumpyArrayValueProvider();
        final Object[][] data = valueProvider.parseValues(evaluateFullValue(node));
        DefaultTableModel model = new DefaultTableModel(data, range(0, data[0].length - 1));

        myTable.setModel(model);

        //for (int i = 0; i < myTable.getColumnModel().getColumnCount(); i++) {
        //  TableColumn column = myTable.getColumnModel().getColumn(i);
        //  column.setPreferredWidth(25);
        //  int width = 0;
        //  for (int row = 0; row < myTable.getRowCount(); row++) {
        //    TableCellRenderer renderer = myTable.getCellRenderer(row, i);
        //    Component comp = myTable.prepareRenderer(renderer, row, i);
        //    width = Math.max (comp.getPreferredSize().width, width);
        //  }
        //  column.setWidth(width);
        //}
      }
    }

    private String[] range(int min, int max) {
      String[] array = new String[max - min + 1];
      for (int i = min; i <= max; i++) {
        array[i] = Integer.toString(i);
      }
      return array;
    }

    private String evaluateFullValue(XValueNodeImpl node) {
      final String[] result = new String[1];

      XFullValueEvaluator.XFullValueEvaluationCallback valueEvaluationCallback = new XFullValueEvaluator.XFullValueEvaluationCallback() {
        @Override
        public void evaluated(@NotNull String fullValue) {
          result[0] = fullValue;
        }

        @Override
        public void evaluated(@NotNull String fullValue, @Nullable Font font) {
          result[0] = fullValue;
        }

        @Override
        public void errorOccurred(@NotNull String errorMessage) {
          result[0] = errorMessage;
        }

        @Override
        public boolean isObsolete() {
          return false;
        }
      };

      if (node.getFullValueEvaluator() != null) {
        node.getFullValueEvaluator().startEvaluation(valueEvaluationCallback);
      }
      else {
        return node.getRawValue();
      }

      return result[0];
    }

    @Override
    @NotNull
    protected Action[] createActions() {
      return new Action[]{getCancelAction()};
    }

    @Override
    protected String getDimensionServiceKey() {
      return "#com.jetbrains.python.actions.PyViewArrayAction";
    }

    @Override
    protected JComponent createCenterPanel() {
      return myComponent;
    }
  }

  private class MyComponent extends JPanel {
    private JScrollPane myScrollPane;
    private JTextField myTextField;
    private JBTable myTable;
    private JCheckBox myCheckBox;

    public MyComponent() {
      super(new GridBagLayout());

      myTextField = new JTextField();
      myTextField.setToolTipText("Format");

      myTable = new JBTable() {
        public boolean getScrollableTracksViewportWidth() {
          return getPreferredSize().width < getParent().getWidth();
        }
      };
      myTable.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);

      myCheckBox = new JCheckBox();
      myCheckBox.setText("Colored");
      myCheckBox.setSelected(true);

      myScrollPane = new JBScrollPane(myTable);
      myScrollPane.setHorizontalScrollBarPolicy(JBScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
      myScrollPane.setVerticalScrollBarPolicy(JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

      add(myScrollPane,
          new GridBagConstraints(0, 0, 2, 1, 1, 0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));
      add(myTextField,
          new GridBagConstraints(0, 1, 1, 1, 1, 0, GridBagConstraints.SOUTH, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));
      add(myCheckBox,
          new GridBagConstraints(1, 1, 1, 1, 1, 0, GridBagConstraints.SOUTH, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));
    }

    public JTextField getTextField() {
      return myTextField;
    }

    public JBTable getTable() {
      return myTable;
    }
  }
}
