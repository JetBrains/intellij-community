package com.intellij.compiler.actions;

import com.intellij.compiler.Chunk;
import com.intellij.compiler.ModuleCompilerUtil;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MultiLineLabelUI;
import com.intellij.util.ListWithSelection;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.ComboBoxTableCellEditor;
import com.intellij.util.ui.Table;
import com.intellij.ui.IdeBorderFactory;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 29, 2004
 */
public class GenerateAntBuildDialog extends DialogWrapper{
  private JPanel myPanel;
  private JRadioButton myGenerateSingleFileBuild;
  private JRadioButton myGenerateMultipleFilesBuild;
  private JCheckBox myEnableUIFormsCompilation;
  private JRadioButton myCbBackupFiles;
  private JRadioButton myCbOverwriteFiles;
  private final Project myProject;
  private static final String SINGLE_FILE_PROPERTY = "GenerateAntBuildDialog.generateSingleFile";
  private static final String UI_FORM_PROPERTY = "GenerateAntBuildDialog.enableUiFormCompile";
  private static final String BACKUP_FILES_PROPERTY = "GenerateAntBuildDialog.backupFiles";
  private JPanel myChunksPanel;
  private MyTableModel myTableModel;
  private Table myTable;

  public GenerateAntBuildDialog(Project project) {
    super(project, false);
    myProject = project;
    setTitle("Generate Ant Build");
    init();
    loadSettings();
  }

  private List<Chunk<Module>> getCycleChunks() {
    List<Chunk<Module>> chunks = ModuleCompilerUtil.getSortedModuleChunks(myProject, ModuleManager.getInstance(myProject).getModules());
    for (Iterator<Chunk<Module>> it = chunks.iterator(); it.hasNext();) {
      final Chunk<Module> chunk = it.next();
      if (chunk.getNodes().size() == 1) {
        it.remove();
      }
    }
    return chunks;
  }

  private void loadSettings() {
    final PropertiesComponent properties = PropertiesComponent.getInstance(myProject);
    final boolean singleFile = properties.isTrueValue(SINGLE_FILE_PROPERTY);
    myGenerateSingleFileBuild.setSelected(singleFile);
    myGenerateMultipleFilesBuild.setSelected(!singleFile);
    final boolean uiForm = properties.isTrueValue(UI_FORM_PROPERTY);
    myEnableUIFormsCompilation.setSelected(uiForm);
    final boolean backup = properties.isTrueValue(BACKUP_FILES_PROPERTY);
    myCbBackupFiles.setSelected(backup);
    myCbOverwriteFiles.setSelected(!backup);
  }

  private void saveSettings() {
    final PropertiesComponent properties = PropertiesComponent.getInstance(myProject);
    properties.setValue(SINGLE_FILE_PROPERTY, Boolean.toString(myGenerateSingleFileBuild.isSelected()));
    properties.setValue(UI_FORM_PROPERTY, Boolean.toString(myEnableUIFormsCompilation.isSelected()));
    properties.setValue(BACKUP_FILES_PROPERTY, Boolean.toString(myCbBackupFiles.isSelected()));
  }

  protected void dispose() {
    saveSettings();
    super.dispose();
  }

  protected JComponent createCenterPanel() {
    final ButtonGroup group = new ButtonGroup();
    group.add(myGenerateMultipleFilesBuild);
    group.add(myGenerateSingleFileBuild);

    final ButtonGroup group1 = new ButtonGroup();
    group1.add(myCbBackupFiles);
    group1.add(myCbOverwriteFiles);

    myGenerateMultipleFilesBuild.setSelected(true);
    myCbBackupFiles.setSelected(true);
    myEnableUIFormsCompilation.setSelected(true);

    initChunksPanel();

    return myPanel;
  }

  private void initChunksPanel() {
    java.util.List<Chunk<Module>> chunks = getCycleChunks();
    if (chunks.size() == 0) {
      return;
    }
    myChunksPanel.setLayout(new BorderLayout());
    myChunksPanel.setBorder(IdeBorderFactory.createTitledBorder("Cyclic Module Dependencies"));
    final String text =
      "Some modules have cyclic dependencies.\n" +
      "In order to generate ant build script, please select the \"main\" (representative) module for each dependency cycle.\n" +
      "The source code for all modules in the cycle will be compiled into the main module's output folders;\n" +
      "All modules in the cycle will use the JSDK assigned to the main module;\n" +
      "Any jar archives created will be named after the name of the main module.";
    JLabel textLabel = new JLabel(text);
    textLabel.setUI(new MultiLineLabelUI());
    textLabel.setBorder(IdeBorderFactory.createEmptyBorder(4, 4, 6, 4));
    myChunksPanel.add(textLabel, BorderLayout.NORTH);

    myTableModel = new MyTableModel(chunks);
    myTable = new Table(myTableModel);
    myTable.fixColumnWidthToHeader(MyTableModel.NUMBER_COLUMN);
    final TableColumn nameColumn = myTable.getColumnModel().getColumn(MyTableModel.NAME_COLUMN);
    nameColumn.setCellEditor(ComboBoxTableCellEditor.INSTANCE);
    nameColumn.setCellRenderer(new MyTableCellRenderer());

    final Dimension preferredSize = new Dimension(myTable.getPreferredSize());
    preferredSize.height = (myTableModel.getRowCount() + 2) * myTable.getRowHeight() + myTable.getTableHeader().getHeight();

    final JScrollPane scrollPane = new JScrollPane(myTable);
    scrollPane.setPreferredSize(preferredSize);
    myChunksPanel.add(scrollPane, BorderLayout.CENTER);
  }

  protected void doOKAction() {
    if (myTable != null) {
      TableCellEditor cellEditor = myTable.getCellEditor();
      if (cellEditor != null) {
        cellEditor.stopCellEditing();
      }
    }
    super.doOKAction();
  }

  public boolean isGenerateSingleFileBuild() {
    return myGenerateSingleFileBuild.isSelected();
  }

  public boolean isFormsCompilationEnabled() {
    return myEnableUIFormsCompilation.isSelected();
  }

  public boolean isBackupFiles() {
    return myCbBackupFiles.isSelected();
  }

  public String[] getRepresentativeModuleNames() {
    return myTableModel != null? myTableModel.getModuleRepresentatives() : ArrayUtil.EMPTY_STRING_ARRAY;
  }

  private static class MyTableModel extends AbstractTableModel {
    private static final int NUMBER_COLUMN = 0;
    private static final int NAME_COLUMN = 1;

    private final List<ListWithSelection> myItems = new ArrayList<ListWithSelection>();

    public MyTableModel(List<Chunk<Module>> chunks) {
      for (Iterator<Chunk<Module>> it = chunks.iterator(); it.hasNext();) {
        final Chunk<Module> chunk = it.next();
        final ListWithSelection item = new ListWithSelection();
        for (Iterator<Module> modulesIterator = chunk.getNodes().iterator(); modulesIterator.hasNext();) {
          final Module module = (Module)modulesIterator.next();
          item.add(module.getName());
        }
        item.selectFirst();
        myItems.add(item);
      }
    }

    public String[] getModuleRepresentatives() {
      final String[] names = new String[myItems.size()];
      int index = 0;
      for (Iterator<ListWithSelection> it = myItems.iterator(); it.hasNext();) {
        final ListWithSelection listWithSelection = it.next();
        names[index++] = (String)listWithSelection.getSelection();
      }
      return names;
    }

    public int getColumnCount() {
      return 2;
    }

    public int getRowCount() {
      return myItems.size();
    }

    public boolean isCellEditable(int rowIndex, int columnIndex) {
      return columnIndex == 1;
    }

    public Class getColumnClass(int columnIndex) {
      switch (columnIndex) {
        case NUMBER_COLUMN : return Integer.class;
        case NAME_COLUMN : return ListWithSelection.class;
        default: return null;
      }
    }

    public Object getValueAt(int rowIndex, int columnIndex) {
      switch (columnIndex) {
        case NUMBER_COLUMN: return new Integer(rowIndex + 1);
        case NAME_COLUMN: return myItems.get(rowIndex);
        default: return null;
      }
    }

    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
      if (columnIndex == NAME_COLUMN) {
        myItems.get(rowIndex).select(aValue);
      }
    }

    public String getColumnName(int columnIndex) {
      switch (columnIndex) {
        case NUMBER_COLUMN : return "N";
        case NAME_COLUMN : return "Main Module";
      }
      return super.getColumnName(columnIndex);
    }
  }

  private static class MyTableCellRenderer extends DefaultTableCellRenderer {
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      if (value instanceof ListWithSelection) {
        value = ((ListWithSelection)value).getSelection();
      }
      final JLabel component = (JLabel)super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      component.setHorizontalAlignment(SwingConstants.CENTER);
      return component;
    }
  }
}
