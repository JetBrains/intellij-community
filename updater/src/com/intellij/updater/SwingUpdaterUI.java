/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.updater;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

@SuppressWarnings({"UseJBColor", "UndesirableClassUsage", "UseDPIAwareInsets", "SSBasedInspection", "UseDPIAwareBorders"})
public class SwingUpdaterUI implements UpdaterUI {
  private static final int RESULT_REQUIRES_RESTART = 42;

  private static final EmptyBorder FRAME_BORDER = new EmptyBorder(8, 8, 8, 8);
  private static final EmptyBorder LABEL_BORDER = new EmptyBorder(0, 0, 5, 0);
  private static final EmptyBorder BUTTONS_BORDER = new EmptyBorder(5, 0, 0, 0);

  private static final String TITLE = "Update";
  private static final String CANCEL_BUTTON_TITLE = "Cancel";
  private static final String EXIT_BUTTON_TITLE = "Exit";
  private static final String PROCEED_BUTTON_TITLE = "Proceed";

  private final Predicate<UpdaterUI> myOperation;

  private final JLabel myProcessTitle;
  private final JProgressBar myProcessProgress;
  private final JLabel myProcessStatus;
  private final JTextArea myConsole;
  private final JPanel myConsolePane;
  private final JButton myCancelButton;
  private final JFrame myFrame;

  private final Queue<Runnable> myQueue = new ConcurrentLinkedQueue<>();
  private final AtomicBoolean isCancelled = new AtomicBoolean(false);
  private final AtomicBoolean isRunning = new AtomicBoolean(false);
  private final AtomicBoolean hasError = new AtomicBoolean(false);
  private boolean myApplied;

  public SwingUpdaterUI(Predicate<UpdaterUI> operation) {
    myOperation = operation;

    myProcessTitle = new JLabel(" ");
    myProcessProgress = new JProgressBar(0, 100);
    myProcessStatus = new JLabel(" ");

    myCancelButton = new JButton(CANCEL_BUTTON_TITLE);

    myConsole = new JTextArea();
    myConsole.setLineWrap(true);
    myConsole.setWrapStyleWord(true);
    myConsole.setCaretPosition(myConsole.getText().length());
    myConsole.setTabSize(1);
    myConsole.setMargin(new Insets(2, 4, 2, 4));
    myConsolePane = new JPanel(new BorderLayout());
    myConsolePane.add(new JScrollPane(myConsole));
    myConsolePane.setBorder(BUTTONS_BORDER);
    myConsolePane.setVisible(false);

    myCancelButton.addActionListener(e -> doCancel());

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

    JPanel processPanel = new JPanel();
    processPanel.setLayout(new BoxLayout(processPanel, BoxLayout.Y_AXIS));
    processPanel.add(myProcessTitle);
    processPanel.add(myProcessProgress);
    processPanel.add(myProcessStatus);

    processPanel.add(myConsolePane);
    for (Component each : processPanel.getComponents()) {
      ((JComponent)each).setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    JPanel buttonsPanel = new JPanel();
    buttonsPanel.setBorder(BUTTONS_BORDER);
    buttonsPanel.setLayout(new BoxLayout(buttonsPanel, BoxLayout.X_AXIS));
    buttonsPanel.add(Box.createHorizontalGlue());
    buttonsPanel.add(myCancelButton);

    myFrame.add(processPanel, BorderLayout.CENTER);
    myFrame.add(buttonsPanel, BorderLayout.SOUTH);

    myFrame.setMinimumSize(new Dimension(500, 50));
    myFrame.pack();
    myFrame.setLocationRelativeTo(null);

    myFrame.setVisible(true);

    myQueue.add(() -> doPerform());

    startRequestDispatching();
  }

  @Override
  public void setDescription(String oldBuildDesc, String newBuildDesc) {
    myProcessTitle.setText("<html>Updating " + oldBuildDesc + " to " + newBuildDesc + "...");
  }

  @Override
  public boolean showWarning(String message) {
    Object[] choices = {"Retry", "Exit"};
    int choice = JOptionPane.showOptionDialog(null, message, "Warning", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, choices, choices[0]);
    return choice == 0;
  }

  private void startRequestDispatching() {
    new Thread("Updater UI Dispatcher") {
      @Override
      public void run() {
        while (true) {
          try {
            //noinspection BusyWait
            Thread.sleep(100);
          }
          catch (InterruptedException e) {
            Runner.printStackTrace(e);
            return;
          }

          List<Runnable> pendingRequests = new ArrayList<>();
          Runnable request;
          while ((request = myQueue.poll()) != null) {
            pendingRequests.add(request);
          }

          SwingUtilities.invokeLater(() -> {
            for (Runnable each : pendingRequests) {
              each.run();
            }
          });
        }
      }
    }.start();
  }

  private void doCancel() {
    if (isRunning.get()) {
      String message = "The patch has not been applied yet.\nAre you sure you want to abort the operation?";
      int result = JOptionPane.showConfirmDialog(myFrame, message, TITLE, JOptionPane.YES_NO_OPTION);
      if (result == JOptionPane.YES_OPTION) {
        isCancelled.set(true);
        myCancelButton.setEnabled(false);
      }
    }
    else {
      exit();
    }
  }

  private void doPerform() {
    isRunning.set(true);

    new Thread(() -> {
      try {
        myApplied = myOperation.test(this);
      }
      catch(Throwable e) {
        Runner.printStackTrace(e);
        showError(e);
      }
      finally {
        isRunning.set(false);

        if (hasError.get()) {
          startProcess("Failed to apply patch");
          setProgress(100);
          myCancelButton.setText(EXIT_BUTTON_TITLE);
          myCancelButton.setEnabled(true);
        }
        else {
          exit();
        }
      }
    }, "swing updater").start();
  }

  private void exit() {
    System.exit(myApplied ? RESULT_REQUIRES_RESTART : 0);
  }

  @Override
  public Map<String, ValidationResult.Option> askUser(List<ValidationResult> validationResults) throws OperationCancelledException {
    Map<String, ValidationResult.Option> result = new HashMap<>();
    try {
      SwingUtilities.invokeAndWait(() -> {
        boolean proceed = true;
        for (ValidationResult result1 : validationResults) {
          if (result1.options.contains(ValidationResult.Option.NONE)) {
            proceed = false;
            break;
          }
        }

        final JDialog dialog = new JDialog(myFrame, TITLE, true);
        dialog.setLayout(new BorderLayout());
        dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

        JPanel buttonsPanel = new JPanel();
        buttonsPanel.setBorder(BUTTONS_BORDER);
        buttonsPanel.setLayout(new BoxLayout(buttonsPanel, BoxLayout.X_AXIS));
        buttonsPanel.add(Box.createHorizontalGlue());

        JButton cancelButton = new JButton(CANCEL_BUTTON_TITLE);
        cancelButton.addActionListener(new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            isCancelled.set(true);
            myCancelButton.setEnabled(false);
            dialog.setVisible(false);
          }
        });
        buttonsPanel.add(cancelButton);

        if (proceed) {
          JButton proceedButton = new JButton(PROCEED_BUTTON_TITLE);
          proceedButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
              dialog.setVisible(false);
            }
          });
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

        for (int i = 0; i < table.getColumnModel().getColumnCount(); i++) {
          TableColumn each = table.getColumnModel().getColumn(i);
          each.setPreferredWidth(MyTableModel.getColumnWidth(i, new Dimension(600, 400).width));
        }

        String message = "<html>Some conflicts were found in the installation area.<br><br>";
        if (proceed) {
          message += "Please select desired solutions from the " + MyTableModel.COLUMNS[MyTableModel.OPTIONS_COLUMN_INDEX] +
                     " column and press " + PROCEED_BUTTON_TITLE + ".<br>" +
                     "If you do not want to proceed with the update, please press " + CANCEL_BUTTON_TITLE + ".</html>";
        }
        else {
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
  public void startProcess(String title) {
    myQueue.add(() -> {
      myProcessStatus.setText(title);
      myProcessProgress.setIndeterminate(false);
      myProcessProgress.setValue(0);
    });
  }

  @Override
  public void setProgress(int percentage) {
    myQueue.add(() -> {
      myProcessProgress.setIndeterminate(false);
      myProcessProgress.setValue(percentage);
    });
  }

  @Override
  public void setProgressIndeterminate() {
    myQueue.add(() -> myProcessProgress.setIndeterminate(true));
  }

  @Override
  public void setStatus(String status) { }

  @Override
  public void showError(Throwable e) {
    hasError.set(true);

    myQueue.add(() -> {
      StringWriter w = new StringWriter();
      if (!myConsolePane.isVisible()) {
        w.write("Temp. directory: ");
        w.write(System.getProperty("java.io.tmpdir"));
        w.write("\n\n");
      }
      e.printStackTrace(new PrintWriter(w));
      w.append("\n");
      myConsole.append(w.getBuffer().toString());
      if (!myConsolePane.isVisible()) {
        myConsole.setCaretPosition(0);
        myConsolePane.setVisible(true);
        myConsolePane.setPreferredSize(new Dimension(10, 200));
        myFrame.pack();
      }
    });
  }

  @Override
  public void checkCancelled() throws OperationCancelledException {
    if (isCancelled.get()) throw new OperationCancelledException();
  }

  private static class MyTableModel extends AbstractTableModel {
    public static final String[] COLUMNS = {"File", "Action", "Problem", "Solution"};
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
      switch (column) {
        case 0:
          return (int)(totalWidth * 0.6);
        default:
          return (int)(totalWidth * 0.15);
      }
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

    public Map<String, ValidationResult.Option> getResult() {
      Map<String, ValidationResult.Option> result = new HashMap<>();
      for (Item each : myItems) {
        result.put(each.validationResult.path, each.option);
      }
      return result;
    }

    private static class Item {
      private ValidationResult validationResult;
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
        MyTableModel tableModel = (MyTableModel)table.getModel();
        Color color = table.getBackground();

        ValidationResult.Kind kind = tableModel.getKind(row);
        if (kind == ValidationResult.Kind.ERROR) {
          color = new Color(255, 175, 175);
        }
        else if (kind == ValidationResult.Kind.CONFLICT) {
          color = new Color(255, 240, 240);
        }

        result.setBackground(color);
      }
      return result;
    }
  }
}