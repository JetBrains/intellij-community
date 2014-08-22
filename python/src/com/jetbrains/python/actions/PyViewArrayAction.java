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
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.editor.impl.EditorFactoryImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.intellij.xdebugger.frame.XFullValueEvaluator;
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.jetbrains.python.PythonFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.*;
import javax.swing.tree.TreeNode;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
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
        String[] rows = showedValues.split("\\]\n( )*\\[");

        Object[][] data = null;
        boolean numeric = isNumeric(showedValues);
        for (int i = 0; i < rows.length; i++) {
          Object[] row;

          if (numeric) {
            String clearedRow =
              rows[i].replace(START_ROW_SEPARATOR, "").replace(END_ROW_SEPARATOR, "").replace("  ", " ").replace("  ", " ").trim();
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
          System.arraycopy(row, 0, data[i], 0, row.length);
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

    public int[] getShape(XValueNodeImpl node) {

      node.getValueContainer().computeChildren(node);

      if (node.getChildCount() > 0) {
        node.getValueContainer().computeChildren(node);
        List<? extends TreeNode> children = node.getChildren();
        for (TreeNode treeNode : children) {
          int x = 1;
        }
      }
      return new int[0];
    }
  }

  private class MyDialog extends DialogWrapper {
    public JTable myTable;
    private XValueNodeImpl myNode;
    private String myNodeName;
    private Project myProject;

    private MyComponent myComponent;

    private MyDialog(Project project, XValueNodeImpl node, @NotNull String nodeName) {
      super(project, false);
      setModal(false);
      setCancelButtonText("Close");
      setCrossClosesWindow(true);

      myNode = node;
      myNodeName = nodeName;
      myProject = project;

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
        int[] shape = ((NumpyArrayValueProvider)valueProvider).getShape(node);
        final Object[][] data = valueProvider.parseValues(evaluateFullValue(node));

        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;
        if (data.length > 0) {
          try {
            for (int i = 0; i < data.length; i++) {
              for (int j = 0; j < data[0].length; j++) {
                double d = Double.parseDouble(data[i][j].toString());
                min = min > d ? d : min;
                max = max < d ? d : max;
              }
            }
          }
          catch (NumberFormatException e) {
            min = 0;
            max = 0;
          }
        }
        else {
          min = 0;
          max = 0;
        }

        DefaultTableModel model = new DefaultTableModel(data, range(0, data[0].length - 1));

        myTable.setModel(model);
        myTable.setDefaultEditor(myTable.getColumnClass(0), new MyTableCellEditor(myProject));
        myTable.setDefaultRenderer(myTable.getColumnClass(0), new MyTableCellRenderer(min, max));
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
      myCheckBox.addItemListener(new ItemListener() {
        @Override
        public void itemStateChanged(ItemEvent e) {
          if (e.getSource() == myCheckBox) {
            if (myTable.getCellRenderer(0, 0) instanceof MyTableCellRenderer) {
              MyTableCellRenderer renderer = (MyTableCellRenderer)myTable.getCellRenderer(0, 0);
              if (myCheckBox.isSelected()) {
                renderer.setColored(true);
              }
              else {
                renderer.setColored(false);
              }
            }
            myScrollPane.repaint();
          }
        }
      });

      myScrollPane = new JBScrollPane(myTable);
      myScrollPane.setHorizontalScrollBarPolicy(JBScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
      myScrollPane.setVerticalScrollBarPolicy(JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

      JTable rowTable = new RowNumberTable(myTable);
      myScrollPane.setRowHeaderView(rowTable);
      myScrollPane.setCorner(JScrollPane.UPPER_LEFT_CORNER,
                             rowTable.getTableHeader());

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


  class MyTableCellRenderer extends DefaultTableCellRenderer {

    double min;
    double max;
    Color minColor;
    Color maxColor;
    boolean colored = true;

    public MyTableCellRenderer(double min, double max) {
      this.min = min;
      this.max = max;
      minColor = new Color(80, 0, 0);
      maxColor = new Color(150, 0, 0);
    }

    public void setColored(boolean colored) {
      this.colored = colored;
    }

    public Component getTableCellRendererComponent(JTable table, Object value,
                                                   boolean isSelected, boolean hasFocus, int rowIndex, int vColIndex) {
      // 'value' is value contained in the cell located at
      // (rowIndex, vColIndex)

      if (isSelected) {
        // cell (and perhaps other cells) are selected
      }

      if (hasFocus) {
        // this cell is the anchor and the table has the focus
      }

      // Configure the component with the specified value
      setText(value.toString());

      // Set tool tip if desired
      setToolTipText((String)value);


      if (max != min) {
        if (colored) {
          try {
            double med = Double.parseDouble(value.toString());
            int r = (int)(minColor.getRed() + Math.round((maxColor.getRed() - minColor.getRed()) / (max - min) * (med - min)));
            this.setBackground(new Color(r % 255, 0, 0));
          }
          catch (NumberFormatException e) {
          }
        }
        else {
          this.setBackground(new Color(255, 255, 255));
        }
      }


      return this;
    }
  }


  class MyTableCellEditor extends AbstractCellEditor implements TableCellEditor {
    Editor myEditor;
    Project myProject;

    public MyTableCellEditor(Project project) {
      super();
      myProject = project;
    }

    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected,
                                                 int rowIndex, int vColIndex) {


      //PyExpressionCodeFragmentImpl fragment = new PyExpressionCodeFragmentImpl(myProject, "array_view.py", value.toString(), true);
      //
      //myEditor = EditorFactoryImpl.getInstance().
      //  createEditor(PsiDocumentManager.getInstance(myProject).getDocument(fragment), myProject);


      myEditor =
        EditorFactoryImpl.getInstance().createEditor(new DocumentImpl(value.toString()), myProject, PythonFileType.INSTANCE, false);


      JComponent editorComponent = myEditor.getContentComponent();

      editorComponent.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
        .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "enterStroke");
      editorComponent.getActionMap().put("enterStroke", new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
          doOKAction();
        }
      });
      editorComponent.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
        .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "escapeStroke");
      editorComponent.getActionMap().put("escapeStroke", new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
          cancelEditing();
        }
      });

      return editorComponent;
    }

    public Object getCellEditorValue() {
      return myEditor.getDocument().getText();
    }

    public void doOKAction() {
      //todo: not performed
      System.out.println("ok");
    }

    public void cancelEditing() {
      System.out.println("esc");
    }
  }

  /*
 *	Use a JTable as a renderer for row numbers of a given main table.
 *  This table must be added to the row header of the scrollpane that
 *  contains the main table.
 */
  public class RowNumberTable extends JTable
    implements ChangeListener, PropertyChangeListener, TableModelListener {
    private JTable main;

    public RowNumberTable(JTable table) {
      main = table;
      main.addPropertyChangeListener(this);
      main.getModel().addTableModelListener(this);

      setFocusable(false);
      setAutoCreateColumnsFromModel(false);
      setSelectionModel(main.getSelectionModel());


      TableColumn column = new TableColumn();
      column.setHeaderValue(" ");
      addColumn(column);
      column.setCellRenderer(new RowNumberRenderer());

      getColumnModel().getColumn(0).setPreferredWidth(50);
      setPreferredScrollableViewportSize(getPreferredSize());
    }

    @Override
    public void addNotify() {
      super.addNotify();

      Component c = getParent();

      //  Keep scrolling of the row table in sync with the main table.

      if (c instanceof JViewport) {
        JViewport viewport = (JViewport)c;
        viewport.addChangeListener(this);
      }
    }

    /*
     *  Delegate method to main table
     */
    @Override
    public int getRowCount() {
      return main.getRowCount();
    }

    @Override
    public int getRowHeight(int row) {
      int rowHeight = main.getRowHeight(row);

      if (rowHeight != super.getRowHeight(row)) {
        super.setRowHeight(row, rowHeight);
      }

      return rowHeight;
    }

    /*
     *  No model is being used for this table so just use the row number
     *  as the value of the cell.
     */
    @Override
    public Object getValueAt(int row, int column) {
      return Integer.toString(row + 1);
    }

    /*
     *  Don't edit data in the main TableModel by mistake
     */
    @Override
    public boolean isCellEditable(int row, int column) {
      return false;
    }

    /*
     *  Do nothing since the table ignores the model
     */
    @Override
    public void setValueAt(Object value, int row, int column) {
    }

    //
    //  Implement the ChangeListener
    //
    public void stateChanged(ChangeEvent e) {
      //  Keep the scrolling of the row table in sync with main table

      JViewport viewport = (JViewport)e.getSource();
      JScrollPane scrollPane = (JScrollPane)viewport.getParent();
      scrollPane.getVerticalScrollBar().setValue(viewport.getViewPosition().y);
    }

    //
    //  Implement the PropertyChangeListener
    //
    public void propertyChange(PropertyChangeEvent e) {
      //  Keep the row table in sync with the main table

      if ("selectionModel".equals(e.getPropertyName())) {
        setSelectionModel(main.getSelectionModel());
      }

      if ("rowHeight".equals(e.getPropertyName())) {
        repaint();
      }

      if ("model".equals(e.getPropertyName())) {
        main.getModel().addTableModelListener(this);
        revalidate();
      }
    }

    //
    //  Implement the TableModelListener
    //
    @Override
    public void tableChanged(TableModelEvent e) {
      revalidate();
    }

    /*
     *  Attempt to mimic the table header renderer
     */
    private class RowNumberRenderer extends DefaultTableCellRenderer {
      public RowNumberRenderer() {
        setHorizontalAlignment(JLabel.CENTER);
      }

      public Component getTableCellRendererComponent(
        JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        if (table != null) {
          JTableHeader header = table.getTableHeader();

          if (header != null) {
            setForeground(header.getForeground());
            setBackground(header.getBackground());
            setFont(header.getFont());
          }
        }

        if (isSelected) {
          setFont(getFont().deriveFont(Font.BOLD));
        }

        setText((value == null) ? "" : value.toString());
        setBorder(UIManager.getBorder("TableHeader.cellBorder"));

        return this;
      }
    }
  }
}
