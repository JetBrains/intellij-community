package com.intellij.updater;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@SuppressWarnings({"UndesirableClassUsage","UseJBColor"}) // Plain Swing
public abstract class SwingUpdaterUI implements UpdaterUI {

  private static final EmptyBorder FRAME_BORDER = new EmptyBorder(8, 8, 8, 8);
  private static final EmptyBorder LABEL_BORDER = new EmptyBorder(0, 0, 5, 0);
  private static final EmptyBorder BUTTONS_BORDER = new EmptyBorder(5, 0, 0, 0);

  private static final String TITLE = "Update";

  private static final String CANCEL_BUTTON_TITLE = "Cancel";

  private static final String PROCEED_BUTTON_TITLE = "Proceed";

  private final AtomicBoolean isCancelled = new AtomicBoolean(false);

  protected abstract Component getParentComponent();
  protected abstract void notifyCancelled();
  protected abstract void exit();

  @Override
  public boolean showWarning(String message) {
    Object[] choices = new Object[] { "Retry", "Exit" };
    int choice = JOptionPane
      .showOptionDialog(getParentComponent(), message, "Warning", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, choices,
                        choices[0]);
    return choice == 0;
  }

  @Override
  public Map<String, ValidationResult.Option> askUser(final List<ValidationResult> validationResults) throws OperationCancelledException {
    if (validationResults.isEmpty()) return Collections.emptyMap();

    final Map<String, ValidationResult.Option> result = new HashMap<String, ValidationResult.Option>();
    try {
      SwingUtilities.invokeAndWait(() -> {
        boolean proceed = true;
        for (ValidationResult result1 : validationResults) {
          if (result1.options.contains(ValidationResult.Option.NONE)) {
            proceed = false;
            break;
          }
        }

        Component parent = getParentComponent();
        final JDialog dialog = parent instanceof Frame ? new JDialog((Frame)parent, TITLE, true)
                               : new JDialog((Dialog)parent, TITLE, true);
        dialog.setLayout(new BorderLayout());
        dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

        JPanel buttonsPanel = new JPanel();
        buttonsPanel.setBorder(BUTTONS_BORDER);
        buttonsPanel.setLayout(new BoxLayout(buttonsPanel, BoxLayout.X_AXIS));
        buttonsPanel.add(Box.createHorizontalGlue());

        JButton cancelButton = new JButton(CANCEL_BUTTON_TITLE);
        cancelButton.addActionListener(e -> {
          isCancelled.set(true);
          notifyCancelled();
          dialog.setVisible(false);
        });
        buttonsPanel.add(cancelButton);

        if (proceed) {
          JButton proceedButton = new JButton(PROCEED_BUTTON_TITLE);
          proceedButton.addActionListener(e -> dialog.setVisible(false));
          buttonsPanel.add(proceedButton);
          dialog.getRootPane().setDefaultButton(proceedButton);
        } else {
          dialog.getRootPane().setDefaultButton(cancelButton);
        }

        JTable table = new JTable();

        table.setCellSelectionEnabled(true);
        table.setDefaultEditor(ValidationResult.Option.class, new MyCellEditor());
        table.setDefaultRenderer(Object.class, new MyCellRenderer());
        MyTableModel model = new MyTableModel(validationResults);
        table.setModel(model);

        for (int i = 0; i < table.getColumnModel().getColumnCount(); i++) {
          TableColumn each = table.getColumnModel().getColumn(i);
          each.setPreferredWidth(MyTableModel.getColumnWidth(i, new Dimension(600, 400).width));
        }

        String message = "<html>Some conflicts were found in the installation area.<br><br>";

        if (proceed) {
          message += "Please select desired solutions from the " + MyTableModel.COLUMNS[MyTableModel.OPTIONS_COLUMN_INDEX] +
                     " column and press " + PROCEED_BUTTON_TITLE + ".<br>" +
                     "If you do not want to proceed with the update, please press " + CANCEL_BUTTON_TITLE + ".</html>";
        } else {
          message += "Some of the conflicts below do not have a solution, so the patch cannot be applied.<br>" +
                     "Press " + CANCEL_BUTTON_TITLE + " to exit.</html>";
        }

        JLabel label = new JLabel(message);
        label.setBorder(LABEL_BORDER);
        dialog.add(label, BorderLayout.NORTH);
        dialog.add(new JScrollPane(table), BorderLayout.CENTER);
        dialog.add(buttonsPanel, BorderLayout.SOUTH);

        dialog.getRootPane().setBorder(FRAME_BORDER);

        dialog.setSize(new Dimension(600, 400));
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);

        result.putAll(model.getResult());
      });
    }
    catch (InterruptedException | InvocationTargetException e) {
      throw new RuntimeException(e);
    }
    checkCancelled();
    return result;
  }

  @Override
  public void checkCancelled() throws OperationCancelledException {
    if (isCancelled.get()) throw new OperationCancelledException();
  }

  private static class MyTableModel extends AbstractTableModel {
    public static final String[] COLUMNS = new String[]{"File", "Action", "Problem", "Solution"};
    public static final int OPTIONS_COLUMN_INDEX = 3;
    private final List<Item> myItems = new ArrayList<Item>();

    public MyTableModel(List<ValidationResult> validationResults) {
      for (ValidationResult each : validationResults) {
        myItems.add(new Item(each, each.options.get(0)));
      }
    }

    @Override
    public int getColumnCount() {
      return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
      return COLUMNS[column];
    }

    public static int getColumnWidth(int column, int totalWidth) {
      switch (column) {
        case 0:
          return (int)(totalWidth * 0.6);
        default:
          return (int)(totalWidth * 0.15);
      }
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
      if (columnIndex == OPTIONS_COLUMN_INDEX) {
        return ValidationResult.Option.class;
      }
      return super.getColumnClass(columnIndex);
    }

    @Override
    public int getRowCount() {
      return myItems.size();
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
      return columnIndex == OPTIONS_COLUMN_INDEX && getOptions(rowIndex).size() > 1;
    }

    @Override
    public void setValueAt(Object value, int rowIndex, int columnIndex) {
      if (columnIndex == OPTIONS_COLUMN_INDEX) {
        myItems.get(rowIndex).option = (ValidationResult.Option)value;
      }
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      Item item = myItems.get(rowIndex);
      switch (columnIndex) {
        case 0:
          return item.validationResult.path;
        case 1:
          return item.validationResult.action;
        case 2:
          return item.validationResult.message;
        case OPTIONS_COLUMN_INDEX:
          return item.option;
      }
      return null;
    }

    public ValidationResult.Kind getKind(int rowIndex) {
      return myItems.get(rowIndex).validationResult.kind;
    }

    public List<ValidationResult.Option> getOptions(int rowIndex) {
      Item item = myItems.get(rowIndex);
      return item.validationResult.options;
    }

    public Map<String, ValidationResult.Option> getResult() {
      Map<String, ValidationResult.Option> result = new HashMap<String, ValidationResult.Option>();
      for (Item each : myItems) {
        result.put(each.validationResult.path, each.option);
      }
      return result;
    }

    private static class Item {
      ValidationResult validationResult;
      ValidationResult.Option option;

      private Item(ValidationResult validationResult, ValidationResult.Option option) {
        this.validationResult = validationResult;
        this.option = option;
      }
    }
  }

  private static class MyCellEditor extends DefaultCellEditor {
    public MyCellEditor() {
      super(new JComboBox());
    }

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
      MyTableModel tableModel = (MyTableModel)table.getModel();
      DefaultComboBoxModel comboModel = new DefaultComboBoxModel();

      for (ValidationResult.Option each : tableModel.getOptions(row)) {
        comboModel.addElement(each);
      }
      ((JComboBox)editorComponent).setModel(comboModel);

      return super.getTableCellEditorComponent(table, value, isSelected, row, column);
    }
  }

  private static class MyCellRenderer extends DefaultTableCellRenderer {
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      Component result = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      if (!isSelected) {
        MyTableModel tableModel = (MyTableModel)table.getModel();
        Color color = table.getBackground();

        switch (tableModel.getKind(row)) {
          case ERROR:
            color = new Color(255, 175, 175);
            break;
          case CONFLICT:
            color = new Color(255, 240, 240);
            break;
        }
        result.setBackground(color);
      }
      return result;
    }
  }
}
