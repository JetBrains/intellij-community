package com.intellij.compiler.actions;

import com.intellij.compiler.Chunk;
import com.intellij.compiler.ModuleCompilerUtil;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MultiLineLabelUI;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ListWithSelection;
import com.intellij.util.ui.ComboBoxTableCellEditor;
import com.intellij.util.ui.Table;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableColumn;
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
  private JRadioButton myRbGenerateSingleFileBuild;
  private JRadioButton myRbGenerateMultipleFilesBuild;
  private JCheckBox myCbEnableUIFormsCompilation;
  private JRadioButton myRbBackupFiles;
  private JRadioButton myRbOverwriteFiles;
  private JCheckBox myCbForceTargetJdk;
  private JPanel myChunksPanel;
  private final Project myProject;
  private static final String SINGLE_FILE_PROPERTY = "GenerateAntBuildDialog.generateSingleFile";
  private static final String UI_FORM_PROPERTY = "GenerateAntBuildDialog.enableUiFormCompile";
  private static final String FORCE_TARGET_JDK_PROPERTY = "GenerateAntBuildDialog.forceTargetJdk";
  private static final String BACKUP_FILES_PROPERTY = "GenerateAntBuildDialog.backupFiles";
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
    if (properties.isValueSet(SINGLE_FILE_PROPERTY)) {
      final boolean singleFile = properties.isTrueValue(SINGLE_FILE_PROPERTY);
      myRbGenerateSingleFileBuild.setSelected(singleFile);
      myRbGenerateMultipleFilesBuild.setSelected(!singleFile);
    }
    if (properties.isValueSet(UI_FORM_PROPERTY)) {
      myCbEnableUIFormsCompilation.setSelected(properties.isTrueValue(UI_FORM_PROPERTY));
    }
    if (properties.isValueSet(FORCE_TARGET_JDK_PROPERTY)) {
      myCbForceTargetJdk.setSelected(properties.isTrueValue(FORCE_TARGET_JDK_PROPERTY));
    }
    if (properties.isValueSet(BACKUP_FILES_PROPERTY)) {
      final boolean backup = properties.isTrueValue(BACKUP_FILES_PROPERTY);
      myRbBackupFiles.setSelected(backup);
      myRbOverwriteFiles.setSelected(!backup);
    }
  }

  private void saveSettings() {
    final PropertiesComponent properties = PropertiesComponent.getInstance(myProject);
    properties.setValue(SINGLE_FILE_PROPERTY, Boolean.toString(myRbGenerateSingleFileBuild.isSelected()));
    properties.setValue(UI_FORM_PROPERTY, Boolean.toString(myCbEnableUIFormsCompilation.isSelected()));
    properties.setValue(FORCE_TARGET_JDK_PROPERTY, Boolean.toString(myCbForceTargetJdk.isSelected()));
    properties.setValue(BACKUP_FILES_PROPERTY, Boolean.toString(myRbBackupFiles.isSelected()));
  }

  protected void dispose() {
    saveSettings();
    super.dispose();
  }

  protected JComponent createCenterPanel() {
    final ButtonGroup group = new ButtonGroup();
    group.add(myRbGenerateMultipleFilesBuild);
    group.add(myRbGenerateSingleFileBuild);

    final ButtonGroup group1 = new ButtonGroup();
    group1.add(myRbBackupFiles);
    group1.add(myRbOverwriteFiles);

    myRbGenerateMultipleFilesBuild.setSelected(true);
    myRbBackupFiles.setSelected(true);
    myCbEnableUIFormsCompilation.setSelected(true);
    myCbForceTargetJdk.setSelected(true);

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
    final MyTableCellRenderer cellRenderer = new MyTableCellRenderer();
    final TableColumn nameColumn = myTable.getColumnModel().getColumn(MyTableModel.NAME_COLUMN);
    nameColumn.setCellEditor(ComboBoxTableCellEditor.INSTANCE);
    nameColumn.setCellRenderer(cellRenderer);
    final TableColumn labelColumn = myTable.getColumnModel().getColumn(MyTableModel.NUMBER_COLUMN);
    labelColumn.setCellRenderer(cellRenderer);

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
    return myRbGenerateSingleFileBuild.isSelected();
  }

  public boolean isFormsCompilationEnabled() {
    return myCbEnableUIFormsCompilation.isSelected();
  }

  public boolean isForceTargetJdk() {
    return myCbForceTargetJdk.isSelected();
  }

  public boolean isBackupFiles() {
    return myRbBackupFiles.isSelected();
  }

  public String[] getRepresentativeModuleNames() {
    return myTableModel != null? myTableModel.getModuleRepresentatives() : ArrayUtil.EMPTY_STRING_ARRAY;
  }

  private static class MyTableModel extends AbstractTableModel {
    private static final int NUMBER_COLUMN = 0;
    private static final int NAME_COLUMN = 1;

    private final List<Pair<String, ListWithSelection>> myItems = new ArrayList<Pair<String, ListWithSelection>>();

    public MyTableModel(List<Chunk<Module>> chunks) {
      for (final Chunk<Module> chunk : chunks) {
        final ListWithSelection item = new ListWithSelection();
        for (final Module module : chunk.getNodes()) {
          item.add(module.getName());
        }
        item.selectFirst();
        myItems.add(new Pair<String, ListWithSelection>(createCycleName(chunk), item));
      }
    }

    private String createCycleName(Chunk<Module> chunk) {
      final StringBuffer buf = new StringBuffer();
      for (Module module : chunk.getNodes()) {
        if (buf.length() > 0) {
          buf.append(", ");
        }
        buf.append(module.getName());
      }
      buf.insert(0, "[");
      buf.append("]");
      return buf.toString();
    }

    public String[] getModuleRepresentatives() {
      final String[] names = new String[myItems.size()];
      int index = 0;
      for (final Pair<String,ListWithSelection> pair : myItems) {
        names[index++] = (String)pair.getSecond().getSelection();
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
        case NUMBER_COLUMN : return String.class;
        case NAME_COLUMN : return ListWithSelection.class;
        default: return super.getColumnClass(columnIndex);
      }
    }

    public Object getValueAt(int rowIndex, int columnIndex) {
      switch (columnIndex) {
        case NUMBER_COLUMN: return myItems.get(rowIndex).getFirst();
        case NAME_COLUMN: return myItems.get(rowIndex).getSecond();
        default: return null;
      }
    }

    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
      if (columnIndex == NAME_COLUMN) {
        myItems.get(rowIndex).getSecond().select(aValue);
      }
    }

    public String getColumnName(int columnIndex) {
      switch (columnIndex) {
        case NUMBER_COLUMN : return "Cycle";
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
