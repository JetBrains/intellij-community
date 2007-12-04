/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.vcs.configurable;

import com.intellij.CommonBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.AbstractTableCellEditor;

import javax.swing.*;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableCellEditor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.io.File;

/**
 * @author yole
 */
public class VcsDirectoryConfigurationPanel extends PanelWithButtons {
  private final Project myProject;
  private final ProjectLevelVcsManager myVcsManager;
  private final TableView<VcsDirectoryMapping> myDirectoryMappingTable;
  private final ComboboxWithBrowseButton myVcsComboBox = new ComboboxWithBrowseButton();
  private final List<ModuleVcsListener> myListeners = new ArrayList<ModuleVcsListener>();

  private final ColumnInfo<VcsDirectoryMapping, String> DIRECTORY = new ColumnInfo<VcsDirectoryMapping, String>(VcsBundle.message("column.info.configure.vcses.directory")) {
    public String valueOf(final VcsDirectoryMapping mapping) {
      String directory = mapping.getDirectory();
      if (directory.length() == 0) {
        return "<Project Root>";
      }
      VirtualFile baseDir = myProject.getBaseDir();
      if (baseDir != null) {
        return FileUtil.getRelativePath(new File(baseDir.getPath()), new File(directory));
      }
      return directory;
    }
  };


  private final ColumnInfo<VcsDirectoryMapping, String> VCS_SETTING = new ColumnInfo<VcsDirectoryMapping, String>(VcsBundle.message("comumn.name.configure.vcses.vcs")) {
    public String valueOf(final VcsDirectoryMapping object) {
      return object.getVcs();
    }

    public boolean isCellEditable(final VcsDirectoryMapping o) {
      return true;
    }

    public void setValue(final VcsDirectoryMapping o, final String aValue) {
      Collection<AbstractVcs> activeVcses = getActiveVcses();
      o.setVcs(aValue);
      checkNotifyListeners(activeVcses);
    }

    public TableCellRenderer getRenderer(final VcsDirectoryMapping p0) {
      return new ColoredTableCellRenderer() {
        protected void customizeCellRenderer(JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
          final String vcsName = p0.getVcs();
          String text;
          if (vcsName.length() == 0) {
            text = VcsBundle.message("none.vcs.presentation");
          }
          else {
            final AbstractVcs vcs = myVcsManager.findVcsByName(vcsName);
            if (vcs != null) {
              text = vcs.getDisplayName();
            }
            else {
              text = VcsBundle.message("unknown.vcs.presentation", vcsName);
            }
          }
          append(text, new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, table.getForeground()));
        }
      };
    }

    @Override
    public TableCellEditor getEditor(final VcsDirectoryMapping o) {
      return new AbstractTableCellEditor() {
        public Object getCellEditorValue() {
          VcsWrapper selectedVcs = (VcsWrapper) myVcsComboBox.getComboBox().getSelectedItem();
          return selectedVcs.getOriginal() == null ? "" : selectedVcs.getOriginal().getName();
        }

        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
          String vcsName = (String) value;
          myVcsComboBox.getComboBox().setSelectedItem(VcsWrapper.fromName(myProject, vcsName));
          return myVcsComboBox;
        }
      };
    }
  };
  private ListTableModel<VcsDirectoryMapping> myModel;
  private JButton myAddButton;
  private JButton myEditButton;
  private JButton myRemoveButton;

  public VcsDirectoryConfigurationPanel(final Project project) {
    myProject = project;
    myVcsManager = ProjectLevelVcsManager.getInstance(project);

    myDirectoryMappingTable = new TableView<VcsDirectoryMapping>();
    initializeModel();

    myVcsComboBox.getComboBox().setModel(buildVcsWrappersModel(myProject));
    myVcsComboBox.getComboBox().setRenderer(new EditorComboBoxRenderer(myVcsComboBox.getComboBox().getEditor()));
    myVcsComboBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final VcsWrapper vcsWrapper = ((VcsWrapper)myVcsComboBox.getComboBox().getSelectedItem());
        AbstractVcs abstractVcs = null;
        if (vcsWrapper != null){
          abstractVcs = vcsWrapper.getOriginal();
        }
        new VcsConfigurationsDialog(project, myVcsComboBox.getComboBox(), abstractVcs).show();
      }
    });

    myDirectoryMappingTable.setRowHeight(myVcsComboBox.getPreferredSize().height);
    myDirectoryMappingTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(final ListSelectionEvent e) {
        updateButtons();
      }
    });
    initPanel();
    updateButtons();
  }

  private void initializeModel() {
    List<VcsDirectoryMapping> mappings = new ArrayList<VcsDirectoryMapping>();
    for(VcsDirectoryMapping mapping: ProjectLevelVcsManager.getInstance(myProject).getDirectoryMappings()) {
      mappings.add(new VcsDirectoryMapping(mapping.getDirectory(), mapping.getVcs(), mapping.getRootSettings()));
    }
    myModel = new ListTableModel<VcsDirectoryMapping>(new ColumnInfo[]{DIRECTORY, VCS_SETTING}, mappings, 0);
    myDirectoryMappingTable.setModel(myModel);
  }

  private void updateButtons() {
    final boolean hasSelection = myDirectoryMappingTable.getSelectedObject() != null;
    myEditButton.setEnabled(hasSelection);
    myRemoveButton.setEnabled(hasSelection);
  }

  public static DefaultComboBoxModel buildVcsWrappersModel(final Project project) {
    final AbstractVcs[] vcss = ProjectLevelVcsManager.getInstance(project).getAllVcss();
    VcsWrapper[] vcsWrappers = new VcsWrapper[vcss.length+1];
    vcsWrappers [0] = new VcsWrapper(null);
    for(int i=0; i<vcss.length; i++) {
      vcsWrappers [i+1] = new VcsWrapper(vcss [i]);
    }
    return new DefaultComboBoxModel(vcsWrappers);
  }

  protected String getLabelText() {
    return null;
  }

  protected JButton[] createButtons() {
    myAddButton = new JButton(CommonBundle.message("button.add"));
    myAddButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        addMapping();
      }
    });
    myEditButton = new JButton(CommonBundle.message("button.edit"));
    myEditButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        editMapping();
      }
    });
    myRemoveButton = new JButton(CommonBundle.message("button.remove"));
    myRemoveButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        removeMapping();
      }
    });
    JButton configureButton = new JButton(VcsBundle.message("button.configure"));
    configureButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        showConfigureDialog();
      }
    });
    return new JButton[] {myAddButton, myEditButton, myRemoveButton, configureButton};
  }

  private void addMapping() {
    Collection<AbstractVcs> activeVcses = getActiveVcses();
    VcsMappingConfigurationDialog dlg = new VcsMappingConfigurationDialog(myProject, VcsBundle.message("directory.mapping.add.title"));
    dlg.show();
    if (dlg.isOK()) {
      VcsDirectoryMapping mapping = new VcsDirectoryMapping();
      dlg.saveToMapping(mapping);
      List<VcsDirectoryMapping> items = new ArrayList<VcsDirectoryMapping>(myModel.getItems());
      items.add(mapping);
      myModel.setItems(items);
      checkNotifyListeners(activeVcses);
    }
  }

  private void editMapping() {
    Collection<AbstractVcs> activeVcses = getActiveVcses();
    VcsMappingConfigurationDialog dlg = new VcsMappingConfigurationDialog(myProject, VcsBundle.message("directory.mapping.remove.title"));
    final VcsDirectoryMapping mapping = myDirectoryMappingTable.getSelectedObject();
    dlg.setMapping(mapping);
    dlg.show();
    if (dlg.isOK()) {
      dlg.saveToMapping(mapping);
      myModel.fireTableDataChanged();
      checkNotifyListeners(activeVcses);
    }
  }

  private void removeMapping() {
    Collection<AbstractVcs> activeVcses = getActiveVcses();
    ArrayList<VcsDirectoryMapping> mappings = new ArrayList<VcsDirectoryMapping>(myModel.getItems());
    int index = myDirectoryMappingTable.getSelectionModel().getMinSelectionIndex();
    Collection<VcsDirectoryMapping> selection = myDirectoryMappingTable.getSelection();
    mappings.removeAll(selection);
    myModel.setItems(mappings);
    if (mappings.size() > 0) {
      if (index >= mappings.size()) {
        index = mappings.size()-1;
      }
      myDirectoryMappingTable.getSelectionModel().setSelectionInterval(index, index);
    }
    checkNotifyListeners(activeVcses);
  }

  private void showConfigureDialog() {
    AbstractVcs defaultVcs = null;
    final VcsDirectoryMapping mapping = myDirectoryMappingTable.getSelectedObject();
    if (mapping != null && mapping.getVcs().length() != 0) {
      defaultVcs = myVcsManager.findVcsByName(mapping.getVcs());
    }
    new VcsConfigurationsDialog(myProject, null, defaultVcs).show();
  }

  protected JComponent createMainComponent() {
    return new JScrollPane(myDirectoryMappingTable);
  }

  public void reset() {
    initializeModel();
  }

  public void apply() {
    myVcsManager.setDirectoryMappings(myModel.getItems());
  }

  public boolean isModified() {
    return !myModel.getItems().equals(myVcsManager.getDirectoryMappings());
  }

  public void addVcsListener(final ModuleVcsListener moduleVcsListener) {
    myListeners.add(moduleVcsListener);
  }

  public void removeVcsListener(final ModuleVcsListener moduleVcsListener) {
    myListeners.remove(moduleVcsListener);
  }

  private void checkNotifyListeners(Collection<AbstractVcs> oldVcses) {
    Collection<AbstractVcs> vcses = getActiveVcses();
    if (!vcses.equals(oldVcses)) {
      for(ModuleVcsListener listener: myListeners) {
        listener.activeVcsSetChanged(vcses);
      }
    }
  }

  public Collection<AbstractVcs> getActiveVcses() {
    Set<AbstractVcs> vcses = new HashSet<AbstractVcs>();
    for(VcsDirectoryMapping mapping: myModel.getItems()) {
      if (mapping.getVcs().length() > 0) {
        vcses.add(myVcsManager.findVcsByName(mapping.getVcs()));
      }
    }
    return vcses;
  }
}