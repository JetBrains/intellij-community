package com.intellij.diagnostic.logging;

import com.intellij.diagnostic.DiagnosticBundle;
import com.intellij.execution.configurations.LogFileOptions;
import com.intellij.execution.configurations.PredefinedLogFile;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.ui.BooleanTableCellRenderer;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TableUtil;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.AbstractTableCellEditor;
import com.intellij.util.ui.CellEditorComponentWithBrowseButton;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: anna
 * Date: Apr 22, 2005
 */
public class LogConfigurationPanel<T extends RunConfigurationBase> extends SettingsEditor<RunConfigurationBase> {
  private TableView myFilesTable;
  private ListTableModel myModel;
  private JPanel myWholePanel;
  private JButton myAddButton;
  private JButton myRemoveButton;
  private JPanel myScrollPanel;
  private Map<LogFileOptions, PredefinedLogFile> myLog2Predefined = new HashMap<LogFileOptions, PredefinedLogFile>();
  private List<PredefinedLogFile> myUnresolvedPredefined = new ArrayList<PredefinedLogFile>();

  private final ColumnInfo<LogFileOptions, Boolean> IS_SHOW = new MyIsActiveColumnInfo();
  private final ColumnInfo<LogFileOptions, LogFileOptions> FILE = new MyLogFileColumnInfo();
  private final ColumnInfo<LogFileOptions, Boolean> IS_SKIP_CONTENT = new MyIsSkippColumnInfo();

  public LogConfigurationPanel() {
    myModel = new ListTableModel<LogFileOptions>(new ColumnInfo[]{IS_SHOW, FILE, IS_SKIP_CONTENT});
    myFilesTable = new TableView(myModel);
    final JTableHeader tableHeader = myFilesTable.getTableHeader();
    final FontMetrics fontMetrics = tableHeader.getFontMetrics(tableHeader.getFont());

    int preferredWidth = fontMetrics.stringWidth(IS_SHOW.getName()) + 20;
    setUpColumnWidth(tableHeader, preferredWidth, 0);

    preferredWidth = fontMetrics.stringWidth(IS_SKIP_CONTENT.getName()) + 20;
    setUpColumnWidth(tableHeader, preferredWidth, 2);

    myFilesTable.setColumnSelectionAllowed(false);
    myFilesTable.setShowGrid(false);
    myFilesTable.setDragEnabled(false);
    myFilesTable.setShowHorizontalLines(false);
    myFilesTable.setShowVerticalLines(false);
    myFilesTable.setIntercellSpacing(new Dimension(0, 0));
    myAddButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        ArrayList<LogFileOptions> newList = new ArrayList<LogFileOptions>(myModel.getItems());
        final LogFileOptions selectedNameLocation = showEditorDialog("", "", false);
        if (selectedNameLocation != null) {
          newList.add(new LogFileOptions(selectedNameLocation.getName(), selectedNameLocation.getPathPattern(), false, true, selectedNameLocation.isShowAll()));
          myModel.setItems(newList);
          int index = myModel.getRowCount() - 1;
          myModel.fireTableRowsInserted(index, index);
          myFilesTable.setRowSelectionInterval(index, index);
        }
      }
    });
    myRemoveButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        TableUtil.stopEditing(myFilesTable);
        final int[] selected = myFilesTable.getSelectedRows();
        if (selected == null || selected.length == 0) return;
        for (int i = selected.length - 1; i >= 0; i--) {
          myModel.removeRow(selected[i]);
        }
        for (int i = selected.length - 1; i >= 0; i--) {
          int idx = selected[i];
          myModel.fireTableRowsDeleted(idx, idx);
        }
        int selection = selected[0];
        if (selection >= myModel.getRowCount()) {
          selection = myModel.getRowCount() - 1;
        }
        if (selection >= 0) {
          myFilesTable.setRowSelectionInterval(selection, selection);
        }
        myFilesTable.requestFocus();
      }
    });
    myRemoveButton.setEnabled(false);
    myFilesTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
         myRemoveButton.setEnabled(myFilesTable.getSelectedRowCount() >=1);
      }
    });
    final JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myFilesTable);
    scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
    myScrollPanel.add(scrollPane, BorderLayout.CENTER);
    myWholePanel.setPreferredSize(new Dimension(-1, 150));
  }

  private void setUpColumnWidth(final JTableHeader tableHeader, final int preferredWidth, int columnIdx) {
    myFilesTable.getColumnModel().getColumn(columnIdx).setCellRenderer(new BooleanTableCellRenderer());
    final TableColumn tableColumn = tableHeader.getColumnModel().getColumn(columnIdx);
    tableColumn.setWidth(preferredWidth);
    tableColumn.setPreferredWidth(preferredWidth);
    tableColumn.setMinWidth(preferredWidth);
    tableColumn.setMaxWidth(preferredWidth);
  }

  public void refreshPredefinedLogFiles(RunConfigurationBase configurationBase) {
    List<LogFileOptions> items = myModel.getItems();
    List<LogFileOptions> newItems = new ArrayList<LogFileOptions>();
    boolean changed = false;
    for (LogFileOptions item : items) {
      final PredefinedLogFile predefined = myLog2Predefined.get(item);
      if (predefined != null) {
        final LogFileOptions options = configurationBase.getOptionsForPredefinedLogFile(predefined);
        if (LogFileOptions.areEqual(item, options)) {
          newItems.add(item);
        }
        else {
          changed = true;
          myLog2Predefined.remove(item);
          if (options == null) {
            myUnresolvedPredefined.add(predefined);
          }
          else {
            newItems.add(options);
            myLog2Predefined.put(options, predefined);
          }
        }
      }
      else {
        newItems.add(item);
      }
    }

    final PredefinedLogFile[] unresolved = myUnresolvedPredefined.toArray(new PredefinedLogFile[myUnresolvedPredefined.size()]);
    for (PredefinedLogFile logFile : unresolved) {
      final LogFileOptions options = configurationBase.getOptionsForPredefinedLogFile(logFile);
      if (options != null) {
        changed = true;
        myUnresolvedPredefined.remove(logFile);
        myLog2Predefined.put(options, logFile);
        newItems.add(options);
      }
    }

    if (changed) {
      myModel.setItems(newItems);
    }
  }

  protected void resetEditorFrom(final RunConfigurationBase configuration) {
    clearModel();
    ArrayList<LogFileOptions> list = new ArrayList<LogFileOptions>();
    final ArrayList<LogFileOptions> logFiles = configuration.getLogFiles();
    for (LogFileOptions setting : logFiles) {
      list.add(new LogFileOptions(setting.getName(), setting.getPathPattern(), setting.isEnabled(), setting.isSkipContent(), setting.isShowAll()));
    }
    myLog2Predefined.clear();
    myUnresolvedPredefined.clear();
    final ArrayList<PredefinedLogFile> predefinedLogFiles = configuration.getPredefinedLogFiles();
    for (PredefinedLogFile predefinedLogFile : predefinedLogFiles) {
      PredefinedLogFile logFile = new PredefinedLogFile(predefinedLogFile);
      final LogFileOptions options = configuration.getOptionsForPredefinedLogFile(logFile);
      if (options != null) {
        myLog2Predefined.put(options, logFile);
        list.add(options);
      }
      else {
        myUnresolvedPredefined.add(logFile);
      }
    }
    myModel.setItems(list);
  }

  protected void applyEditorTo(final RunConfigurationBase configuration) throws ConfigurationException {
    myFilesTable.stopEditing();
    configuration.removeAllLogFiles();
    configuration.removeAllPredefinedLogFiles();

    for (int i = 0; i < myModel.getRowCount(); i++) {
      LogFileOptions pair = (LogFileOptions)myModel.getValueAt(i, 1);
      if (Comparing.equal(pair.getPathPattern(),"")){
        continue;
      }
      final Boolean checked = (Boolean)myModel.getValueAt(i, 0);
      final Boolean skipped = (Boolean)myModel.getValueAt(i, 2);
      final PredefinedLogFile predefined = myLog2Predefined.get(pair);
      if (predefined != null && !isPredefinedChanged(configuration, predefined, pair)) {
        configuration.addPredefinedLogFile(new PredefinedLogFile(predefined.getId(), pair.isEnabled()));
      }
      else {
        configuration.addLogFile(pair.getPathPattern(), pair.getName(), checked.booleanValue(), skipped.booleanValue(), pair.isShowAll());
      }
    }
    for (PredefinedLogFile logFile : myUnresolvedPredefined) {
      configuration.addPredefinedLogFile(logFile);
    }
  }

  private static boolean isPredefinedChanged(final RunConfigurationBase configuration, final PredefinedLogFile predefined, final LogFileOptions options) {
    final LogFileOptions oldOptions = configuration.getOptionsForPredefinedLogFile(predefined);
    return oldOptions == null || !oldOptions.getPathPattern().equals(options.getPathPattern()) ||
           oldOptions.isSkipContent() != options.isSkipContent() || oldOptions.isShowAll() != options.isShowAll() ||
           !oldOptions.getName().equals(options.getName());
  }

  @NotNull
  protected JComponent createEditor() {
    return myWholePanel;
  }

  protected void disposeEditor() {

  }

  public JComponent getLoggerComponent() {
    return getComponent();
  }

  public void addLogFile(String file, String alias, boolean isShowAll, boolean checked, boolean skipContent) {
    final List<LogFileOptions> itemsUnmodifiable = myModel.getItems();
    List<LogFileOptions> items = new ArrayList<LogFileOptions>();
    items.addAll(itemsUnmodifiable);
    items.add(new LogFileOptions(alias, file, checked, skipContent, isShowAll));
    myModel.setItems(items);
  }

  private void clearModel() {
    myModel.setItems(new ArrayList<LogFileOptions>());
  }

 @Nullable
  private static LogFileOptions showEditorDialog(String name, String location, boolean showAll){
    EditLogPatternDialog dialog = new EditLogPatternDialog();
    dialog.init(name, location, showAll);
    dialog.show();
    if (dialog.isOK()) {
      location = dialog.getLogPattern();
      name = dialog.getName();
      showAll = dialog.isShowAllFiles();
      return new LogFileOptions(name, location, false, false, showAll);
    }
    return null;
  }

  private class MyLogFileColumnInfo extends ColumnInfo<LogFileOptions, LogFileOptions> {
    public MyLogFileColumnInfo() {
      super(DiagnosticBundle.message("log.monitor.log.file.column"));
    }

    public TableCellRenderer getRenderer(final LogFileOptions p0) {
      return new DefaultTableCellRenderer() {
        public Component getTableCellRendererComponent(JTable table,
                                                       Object value,
                                                       boolean isSelected,
                                                       boolean hasFocus,
                                                       int row,
                                                       int column) {
          final Component renderer = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
          setText(((LogFileOptions)value).getName());
          setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
          setBorder(null);
          return renderer;
        }
      };
    }

    public LogFileOptions valueOf(final LogFileOptions object) {
      return object;
    }

    public TableCellEditor getEditor(final LogFileOptions item) {
      return new LogFileCellEditor(item);
    }

    public void setValue(final LogFileOptions o, final LogFileOptions aValue) {
      if (aValue != null) {
        if (!o.getName().equals(aValue.getName()) || !o.getPathPattern().equals(aValue.getPathPattern())
          || o.isShowAll() != aValue.isShowAll()) {
          myLog2Predefined.remove(o);
        }
        o.setName(aValue.getName());
        o.setLast(!aValue.isShowAll());
        o.setPathPattern(aValue.getPathPattern());
      }
    }

    public boolean isCellEditable(final LogFileOptions o) {
      return true;
    }
  }

  private class MyIsActiveColumnInfo extends ColumnInfo<LogFileOptions, Boolean> {
    protected MyIsActiveColumnInfo() {
      super(DiagnosticBundle.message("log.monitor.is.active.column"));
    }

    public Class getColumnClass() {
      return Boolean.class;
    }

    public Boolean valueOf(final LogFileOptions object) {
      return object.isEnabled();
    }

    public boolean isCellEditable(LogFileOptions element) {
      return true;
    }

    public void setValue(LogFileOptions element, Boolean checked){
      final PredefinedLogFile predefinedLogFile = myLog2Predefined.get(element);
      if (predefinedLogFile != null) {
        predefinedLogFile.setEnabled(checked.booleanValue());
      }
      element.setEnable(checked.booleanValue());
    }
  }

  private class MyIsSkippColumnInfo extends ColumnInfo<LogFileOptions, Boolean> {
    protected MyIsSkippColumnInfo() {
      super(DiagnosticBundle.message("log.monitor.is.skipped.column"));
    }

    public Class getColumnClass() {
      return Boolean.class;
    }

    public Boolean valueOf(final LogFileOptions element) {
      return element.isSkipContent();
    }

    public boolean isCellEditable(LogFileOptions element) {
      return true;
    }

    public void setValue(LogFileOptions element, Boolean skipped) {
      if (element.isSkipContent() != skipped.booleanValue()) {
        myLog2Predefined.remove(element);
      }
      element.setSkipContent(skipped.booleanValue());
    }
  }

  private class LogFileCellEditor extends AbstractTableCellEditor {
    private final CellEditorComponentWithBrowseButton<JTextField> myComponent;
    private LogFileOptions myLogFileOptions;

    public LogFileCellEditor(LogFileOptions options) {
      myLogFileOptions = options;
      myComponent = new CellEditorComponentWithBrowseButton<JTextField>(new TextFieldWithBrowseButton(), this);
      getChildComponent().setEditable(false);
      getChildComponent().setBorder(null);
      myComponent.getComponentWithButton().getButton().addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          final LogFileOptions newValue = showEditorDialog(myLogFileOptions.getName(), myLogFileOptions.getPathPattern(), myLogFileOptions.isShowAll());
          if (newValue != null) {
            myLog2Predefined.remove(myLogFileOptions);
            myLogFileOptions = newValue;
          }
          JTextField textField = getChildComponent();
          textField.setText(myLogFileOptions.getName());
          textField.requestFocus();
        }
      });
    }

    public Object getCellEditorValue() {
      return myLogFileOptions;
    }

    private JTextField getChildComponent() {
      return myComponent.getChildComponent();
    }

    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
      getChildComponent().setText(((LogFileOptions)value).getName());
      return myComponent;
    }
  }
}
