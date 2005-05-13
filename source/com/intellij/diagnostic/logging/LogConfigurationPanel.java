package com.intellij.diagnostic.logging;

import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TableUtil;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.AbstractTableCellEditor;
import com.intellij.util.ui.CellEditorComponentWithBrowseButton;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;

/**
 * User: anna
 * Date: Apr 22, 2005
 */
public class LogConfigurationPanel extends SettingsEditor<RunConfigurationBase>{
  private TableView myFilesTable;
  private ListTableModel myModel;
  private JPanel myWholePanel = new JPanel(new BorderLayout());
  private Project myProject;
  private JButton myAddButton;
  private JButton myRemoveButton;
  private JPanel myButtonsPanel;


  private final ColumnInfo<MyFileListElement, Boolean> IS_SHOW = new MyShowInConsoleColumnInfo();
  private final ColumnInfo<MyFileListElement, String > FILE = new MyLogFileColumnInfo();

  public LogConfigurationPanel(Project project) {
    myProject = project;
    myModel = new ListTableModel(new ColumnInfo[]{IS_SHOW, FILE});
    myFilesTable = new TableView(myModel);
    myFilesTable.setShowGrid(false);
    myFilesTable.getColumnModel().getColumn(0).setCellRenderer(new TableCellRenderer() {
      public Component getTableCellRendererComponent(JTable table, Object value,
                                                     boolean isSelected, boolean hasFocus,
                                                     int row, int column) {
        final Component component = myFilesTable.getDefaultRenderer(Boolean.class).getTableCellRendererComponent(table, value, isSelected, hasFocus,
                                                                                                                 row, column);
        if (component instanceof JComponent) {
          ((JComponent)component).setBorder(null);
        }
        return component;
      }
    });
    myFilesTable.setColumnSelectionAllowed(false);
    myAddButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
         ArrayList<MyFileListElement> newList = new ArrayList<MyFileListElement>(myModel.getItems());
         final FileChooserDialog fileChooser = FileChooserFactory.getInstance().createFileChooser(new FileChooserDescriptor(true, false, false,
                                                                                                                                    false, false, true),
                                                                                                          myProject);
         final VirtualFile[] virtualFiles = fileChooser.choose(null, myProject);
         for (int i = 0; i < virtualFiles.length; i++) {
           VirtualFile virtualFile = virtualFiles[i];
           newList.add(new MyFileListElement(virtualFile.getPath(), true));
         }
         myModel.setItems(newList);
      }
    });
    myRemoveButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        TableUtil.stopEditing(myFilesTable);
          int index = myFilesTable.getSelectedRow();
          if (0 <= index && index < myModel.getRowCount()) {
            myModel.removeRow(index);
            if (index < myModel.getRowCount()) {
              myFilesTable.setRowSelectionInterval(index, index);
            }
            else {
              if (index > 0) {
                myFilesTable.setRowSelectionInterval(index - 1, index - 1);
              }
            }
          }

          myFilesTable.requestFocus();
      }
    });
    final JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myFilesTable);
    scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
    myWholePanel.add(scrollPane, BorderLayout.CENTER);
    myWholePanel.add(myButtonsPanel, BorderLayout.EAST);
    myWholePanel.setBorder(BorderFactory.createTitledBorder("Log Files To Show In Console"));
    myWholePanel.setPreferredSize(new Dimension(-1, 150));
  }

  private void clearModel(){
    for (int i = 0; i < myModel.getRowCount(); i++) {
      myModel.removeRow(i);
    }
  }

  protected void resetEditorFrom(final RunConfigurationBase configuration) {
    clearModel();
    ArrayList<MyFileListElement> list = new ArrayList<MyFileListElement>();
    final Map<String , Boolean> logFiles = configuration.getLogFiles();
    for (Iterator<String > iterator = logFiles.keySet().iterator(); iterator.hasNext();) {
      String file = iterator.next();
      list.add(new MyFileListElement(file, logFiles.get(file).booleanValue()));
    }
    myModel.setItems(list);
  }

  protected void applyEditorTo(final RunConfigurationBase configuration) throws ConfigurationException {
    configuration.removeAllLogFiles();
    for (int i = 0; i < myModel.getRowCount(); i++) {
      final String file = (String)myModel.getValueAt(i, 1);
      final Boolean checked = (Boolean)myModel.getValueAt(i, 0);
      configuration.addLogFile(file, checked.booleanValue());
    }
  }

  protected JComponent createEditor() {
    return myWholePanel;
  }

  protected void disposeEditor() {

  }

  public void saveTo(final RunConfigurationBase s) {
    try {
      applyEditorTo(s);
    }
    catch (ConfigurationException e) {
    }
  }

  public void restoreFrom(final RunConfigurationBase s) {
    resetEditorFrom(s);
  }

  public JComponent getLoggerComponent() {
    return getComponent();
  }

  private static class MyFileListElement {
    private String myFile;
    private boolean myEnabled;

    public MyFileListElement(final String file, final boolean enabled) {
      myFile = file;
      myEnabled = enabled;
    }

    public String getFile() {
      return myFile;
    }

    public boolean isEnabled() {
      return myEnabled;
    }

    public void setEnabled(final boolean enabled) {
      myEnabled = enabled;
    }
  }

  private class MyLogFileColumnInfo extends ColumnInfo<MyFileListElement, String >{
    public MyLogFileColumnInfo() {
      super("");
    }

    public String valueOf(final MyFileListElement object) {
      return object.getFile();
    }

    public TableCellEditor getEditor(final MyFileListElement item) {
      return new AbstractTableCellEditor() {
        private TextFieldWithBrowseButton myFileChooser;
        public Object getCellEditorValue() {
          return myFileChooser.getText();
        }

        public Component getTableCellEditorComponent(JTable table, Object value,
                                                     boolean isSelected,
                                                     int row, int column) {
          myFileChooser = new TextFieldWithBrowseButton();
          myFileChooser.addBrowseFolderListener("Choose Log File",
                                                "Choose Log File",
                                                myProject,
                                                new FileChooserDescriptor(true, false, false, false, false, true),
                                                TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT);
          final CellEditorComponentWithBrowseButton rendererComponent = new CellEditorComponentWithBrowseButton(myFileChooser, this);
          rendererComponent.setBorder(null);
          return rendererComponent;
        }
      };
    }

    public boolean isCellEditable(final MyFileListElement o) {
      return true;
    }
  }


  private class MyShowInConsoleColumnInfo extends ColumnInfo<MyFileListElement, Boolean> {
    protected MyShowInConsoleColumnInfo() {
      super("");
    }

    public Class getColumnClass() {
      return Boolean.class;
    }

    public Boolean valueOf(final MyFileListElement object) {
      return new Boolean(object.isEnabled());
    }

    public boolean isCellEditable(MyFileListElement element) {
      return true;
    }

    public void setValue(MyFileListElement element, Boolean checked){
      element.setEnabled(checked.booleanValue());
    }

    public int getWidth(final JTable table) {
      return new JCheckBox("").getMaximumSize().width;
    }
  }

}
