// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.updater;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings({"UndesirableClassUsage", "UseJBColor", "UseDPIAwareInsets", "UseDPIAwareBorders"})
public class SwingUpdaterUI implements UpdaterUI {
  private static final EmptyBorder FRAME_BORDER = new EmptyBorder(8, 8, 8, 8);
  private static final EmptyBorder LABEL_BORDER = new EmptyBorder(0, 0, 5, 0);
  private static final EmptyBorder BUTTONS_BORDER = new EmptyBorder(5, 0, 0, 0);

  private static final Color VALIDATION_ERROR_COLOR = new Color(255, 175, 175);
  private static final Color VALIDATION_CONFLICT_COLOR = new Color(255, 240, 240);

  private static final String TITLE = "Update";
  private static final String CANCEL_BUTTON_TITLE = "Cancel";
  private static final String EXIT_BUTTON_TITLE = "Exit";
  private static final String RETRY_BUTTON_TITLE = "Retry";
  private static final String PROCEED_BUTTON_TITLE = "Proceed";

  private final JLabel myProcessTitle;
  private final JProgressBar myProcessProgress;
  private final JLabel myProcessStatus;
  private final JButton myCancelButton;
  private final JFrame myFrame;

  private volatile boolean myCancelled = false;
  private volatile boolean myPaused = false;

  public SwingUpdaterUI() {
    myProcessTitle = new JLabel(" ");
    myProcessProgress = new JProgressBar(0, 100);
    myProcessStatus = new JLabel(" ");

    myCancelButton = new JButton(CANCEL_BUTTON_TITLE);
    myCancelButton.addActionListener(e -> doCancel());

    JPanel processPanel = new JPanel();
    processPanel.setLayout(new BoxLayout(processPanel, BoxLayout.Y_AXIS));
    processPanel.add(myProcessTitle);
    processPanel.add(myProcessProgress);
    processPanel.add(myProcessStatus);
    for (Component each : processPanel.getComponents()) {
      ((JComponent)each).setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    JPanel buttonsPanel = new JPanel();
    buttonsPanel.setBorder(BUTTONS_BORDER);
    buttonsPanel.setLayout(new BoxLayout(buttonsPanel, BoxLayout.X_AXIS));
    buttonsPanel.add(Box.createHorizontalGlue());
    buttonsPanel.add(myCancelButton);

    myFrame = new JFrame();
    myFrame.setTitle(TITLE);
    myFrame.setLayout(new BorderLayout());
    myFrame.getRootPane().setBorder(FRAME_BORDER);
    myFrame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    myFrame.addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent e) {
        doCancel();
      }
    });
    myFrame.add(processPanel, BorderLayout.CENTER);
    myFrame.add(buttonsPanel, BorderLayout.SOUTH);
    myFrame.setMinimumSize(new Dimension(500, 50));
    myFrame.pack();
    myFrame.setLocationRelativeTo(null);
    myFrame.setVisible(true);

    invokeAndWait(() -> {});
  }

  private void doCancel() {
    if (!myCancelled) {
      myPaused = true;
      String message = "The patch has not been applied yet.\nAre you sure you want to abort the operation?";
      int result = JOptionPane.showConfirmDialog(myFrame, message, TITLE, JOptionPane.YES_NO_OPTION);
      if (result == JOptionPane.YES_OPTION) {
        myCancelled = true;
        myCancelButton.setEnabled(false);
      }
      myPaused = false;
    }
  }

  @Override
  public void setDescription(String oldBuildDesc, String newBuildDesc) {
    invokeLater(() -> myProcessTitle.setText("Updating " + oldBuildDesc + " to " + newBuildDesc + " ..."));
  }

  @Override
  public void startProcess(String title) {
    invokeLater(() -> {
      myProcessStatus.setText(title);
      myProcessProgress.setIndeterminate(false);
      myProcessProgress.setValue(0);
    });
  }

  @Override
  public void setProgress(int percentage) {
    invokeLater(() -> {
      myProcessProgress.setIndeterminate(false);
      myProcessProgress.setValue(percentage);
    });
  }

  @Override
  public void setProgressIndeterminate() {
    invokeLater(() -> myProcessProgress.setIndeterminate(true));
  }

  @Override
  public void checkCancelled() throws OperationCancelledException {
    while (myPaused) Utils.pause(10);
    if (myCancelled) throw new OperationCancelledException();
  }

  @Override
  public void showError(String message) {
    String html = "<html>" + message.replace("\n", "<br>") + "</html>";
    invokeAndWait(() -> JOptionPane.showMessageDialog(myFrame, html, "Update Error", JOptionPane.ERROR_MESSAGE));
  }

  @Override
  public void askUser(String message) throws OperationCancelledException {
    invokeAndWait(() -> {
      if (myCancelled) return;

      Object[] choices = {RETRY_BUTTON_TITLE, EXIT_BUTTON_TITLE};
      int choice = JOptionPane.showOptionDialog(
        myFrame, message, TITLE, JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, choices, choices[0]);

      if (choice != 0) {
        myCancelled = true;
        myCancelButton.setEnabled(false);
      }
    });

    checkCancelled();
  }

  @Override
  public Map<String, ValidationResult.Option> askUser(List<ValidationResult> validationResults) throws OperationCancelledException {
    boolean canProceed = validationResults.stream().noneMatch(r -> r.options.contains(ValidationResult.Option.NONE));
    Map<String, ValidationResult.Option> result = new HashMap<>();

    invokeAndWait(() -> {
      if (myCancelled) return;

      JDialog dialog = new JDialog(myFrame, TITLE, true);
      dialog.setLayout(new BorderLayout());
      dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

      JPanel buttonsPanel = new JPanel();
      buttonsPanel.setBorder(BUTTONS_BORDER);
      buttonsPanel.setLayout(new BoxLayout(buttonsPanel, BoxLayout.X_AXIS));
      buttonsPanel.add(Box.createHorizontalGlue());

      JButton cancelButton = new JButton(CANCEL_BUTTON_TITLE);
      cancelButton.addActionListener(e -> {
        myCancelled = true;
        myCancelButton.setEnabled(false);
        dialog.setVisible(false);
      });
      buttonsPanel.add(cancelButton);

      if (canProceed) {
        JButton proceedButton = new JButton(PROCEED_BUTTON_TITLE);
        proceedButton.addActionListener(e -> dialog.setVisible(false));
        buttonsPanel.add(proceedButton);
        dialog.getRootPane().setDefaultButton(proceedButton);
      }
      else {
        dialog.getRootPane().setDefaultButton(cancelButton);
      }

      JTable table = new JTable();
      table.setCellSelectionEnabled(true);
      table.setDefaultEditor(ValidationResult.Option.class, new MyCellEditor());
      table.setDefaultRenderer(Object.class, new MyCellRenderer());

      MyTableModel model = new MyTableModel(validationResults);
      table.setModel(model);
      table.setRowHeight(new JLabel("X").getPreferredSize().height);

      TableColumnModel columnModel = table.getColumnModel();
      for (int i = 0; i < columnModel.getColumnCount(); i++) {
        columnModel.getColumn(i).setPreferredWidth(MyTableModel.getColumnWidth(i, 1000));
      }

      String message = "<html>Some conflicts were found in the installation area.<br><br>";
      if (canProceed) {
        message += "Please select desired solutions from the '" + MyTableModel.COLUMNS[MyTableModel.OPTIONS_COLUMN_INDEX] + "' " +
                   "column and press '" + PROCEED_BUTTON_TITLE + "'.<br>" +
                   "If you do not want to proceed with the update, please press '" + CANCEL_BUTTON_TITLE + "'.</html>";
      }
      else {
        message += "Some of the conflicts below do not have a solution, so the patch cannot be applied.<br>" +
                   "Press '" + CANCEL_BUTTON_TITLE + "' to exit.</html>";
      }
      JLabel label = new JLabel(message);
      label.setBorder(LABEL_BORDER);

      dialog.add(label, BorderLayout.NORTH);
      dialog.add(new JScrollPane(table), BorderLayout.CENTER);
      dialog.add(buttonsPanel, BorderLayout.SOUTH);
      dialog.getRootPane().setBorder(FRAME_BORDER);
      dialog.setPreferredSize(new Dimension(1000, 500));
      dialog.pack();
      dialog.setLocationRelativeTo(null);
      dialog.setVisible(true);

      model.collectOptions(result);
    });

    checkCancelled();
    return result;
  }

  @Override
  public String bold(String text) {
    return "<b>" + text + "</b>";
  }

  @SuppressWarnings("SSBasedInspection")
  private static void invokeLater(Runnable runnable) {
    SwingUtilities.invokeLater(runnable);
  }

  @SuppressWarnings("SSBasedInspection")
  private static void invokeAndWait(Runnable runnable) {
    try {
      SwingUtilities.invokeAndWait(runnable);
    }
    catch (InterruptedException | InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }

  private static class MyTableModel extends AbstractTableModel {
    public static final String[] COLUMNS = {"File", "Action", "Problem", "Solution"};
    public static final double[] WIDTHS = {0.65, 0.1, 0.15, 0.1};
    public static final int OPTIONS_COLUMN_INDEX = 3;

    private final List<Item> myItems = new ArrayList<>();

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
      return (int)(totalWidth * WIDTHS[column]);
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
      return columnIndex == OPTIONS_COLUMN_INDEX ? ValidationResult.Option.class : super.getColumnClass(columnIndex);
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

    public void collectOptions(Map<String, ValidationResult.Option> result) {
      for (Item each : myItems) {
        result.put(each.validationResult.path, each.option);
      }
    }

    private static class Item {
      private final ValidationResult validationResult;
      private ValidationResult.Option option;

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
      DefaultComboBoxModel<ValidationResult.Option> comboModel = new DefaultComboBoxModel<>();

      for (ValidationResult.Option each : ((MyTableModel)table.getModel()).getOptions(row)) {
        comboModel.addElement(each);
      }

      @SuppressWarnings("unchecked") JComboBox<ValidationResult.Option> comboBox = (JComboBox<ValidationResult.Option>)editorComponent;
      comboBox.setModel(comboModel);

      return super.getTableCellEditorComponent(table, value, isSelected, row, column);
    }
  }

  private static class MyCellRenderer extends DefaultTableCellRenderer {
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      Component result = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

      if (!isSelected) {
        ValidationResult.Kind kind = ((MyTableModel)table.getModel()).getKind(row);
        if (kind == ValidationResult.Kind.ERROR) {
          result.setBackground(VALIDATION_ERROR_COLOR);
        }
        else if (kind == ValidationResult.Kind.CONFLICT) {
          result.setBackground(VALIDATION_CONFLICT_COLOR);
        }
      }

      return result;
    }
  }
}