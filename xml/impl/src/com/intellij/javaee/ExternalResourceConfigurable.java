/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.javaee;

import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.options.OptionalConfigurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.AddEditRemovePanel;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.Table;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ExternalResourceConfigurable extends BaseConfigurable implements SearchableConfigurable, OptionalConfigurable {

  private JPanel myPanel;
  private List<EditLocationDialog.NameLocationPair> myPairs;
  private List<String> myIgnoredUrls;
  private AddEditRemovePanel<EditLocationDialog.NameLocationPair> myExtPanel;
  private AddEditRemovePanel<String> myIgnorePanel;
  private final Project myProject;

  public ExternalResourceConfigurable(Project project) {
    myProject = project;
  }

  public String getDisplayName() {
    return XmlBundle.message("display.name.edit.external.resource");
  }

  public JComponent createComponent() {
    myPanel = new JPanel(new GridBagLayout()) {
      public Dimension getPreferredSize() {
        return new Dimension(700, 400);
      }
    };

    myExtPanel = new AddEditRemovePanel<EditLocationDialog.NameLocationPair>(new ExtUrlsTableModel(), myPairs, XmlBundle.message("label.edit.external.resource.configure.external.resources")) {
      protected EditLocationDialog.NameLocationPair addItem() {
        return addExtLocation();
      }

      protected boolean removeItem(EditLocationDialog.NameLocationPair o) {
        setModified(true);
        return true;
      }

      protected EditLocationDialog.NameLocationPair editItem(EditLocationDialog.NameLocationPair o) {
        return editExtLocation(o);
      }
    };

    myExtPanel.setRenderer(1, new PathRenderer());

    JTable table = myExtPanel.getTable();
    TableColumn column = table.getColumn(table.getColumnName(2));
    column.setMaxWidth(50);
    column.setCellEditor(Table.createBooleanEditor());

    table.getModel().addTableModelListener(new TableModelListener() {
      public void tableChanged(TableModelEvent e) {
        setModified(true);
      }
    });
    myIgnorePanel = new AddEditRemovePanel<String>(new IgnoredUrlsModel(), myIgnoredUrls, XmlBundle.message("label.edit.external.resource.configure.ignored.resources")) {
      protected String addItem() {
        return addIgnoreLocation();
      }

      protected boolean removeItem(String o) {
        setModified(true);
        return true;
      }

      protected String editItem(String o) {
        return editIgnoreLocation(o);
      }
    };

    myPanel.add(myExtPanel,
                new GridBagConstraints(0, 0, 1, 1, 1, 1, GridBagConstraints.NORTH, GridBagConstraints.BOTH, new Insets(5, 2, 4, 2), 0, 0));
    myPanel.add(myIgnorePanel,
                new GridBagConstraints(0, 1, 1, 1, 1, 1, GridBagConstraints.NORTH, GridBagConstraints.BOTH, new Insets(5, 2, 4, 2), 0, 0));

    myExtPanel.setData(myPairs);
    myIgnorePanel.setData(myIgnoredUrls);
    
    return myPanel;
  }

  public Icon getIcon() {
    return IconLoader.getIcon("/general/configurableExternalResources.png");
  }

  public void apply() {
    ExternalResourceManagerEx manager = ExternalResourceManagerEx.getInstanceEx();

    manager.clearAllResources(myProject);
    for (Object myPair : myPairs) {
      EditLocationDialog.NameLocationPair pair = (EditLocationDialog.NameLocationPair)myPair;
      String s = pair.myLocation.replace('\\', '/');
      if (pair.myShared) {
        manager.addResource(pair.myName, s);
      } else {
        manager.addResource(pair.myName, s, myProject);
      }
    }

    for (Object myIgnoredUrl : myIgnoredUrls) {
      String url = (String)myIgnoredUrl;
      manager.addIgnoredResource(url);
    }
    setModified(false);
  }

  public void reset() {

    myPairs = new ArrayList<EditLocationDialog.NameLocationPair>();
    ExternalResourceManagerEx manager = ExternalResourceManagerEx.getInstanceEx();

    String[] urls = manager.getAvailableUrls();
    for (String url : urls) {
      String loc = manager.getResourceLocation(url, myProject);
      myPairs.add(new EditLocationDialog.NameLocationPair(url, loc, true));
    }
    urls = manager.getAvailableUrls(myProject);
    for (String url : urls) {
      String loc = manager.getResourceLocation(url, myProject);
      myPairs.add(new EditLocationDialog.NameLocationPair(url, loc, false));
    }

    Collections.sort(myPairs);

    myIgnoredUrls = new ArrayList<String>();
    final String[] ignoredResources = manager.getIgnoredResources();
    ContainerUtil.addAll(myIgnoredUrls, ignoredResources);

    Collections.sort(myIgnoredUrls);

    if (myExtPanel != null) {
      myExtPanel.setData(myPairs);
      myIgnorePanel.setData(myIgnoredUrls);
    }

    setModified(false);
  }

  public void disposeUIResources() {
    myPanel = null;
    myExtPanel = null;
    myIgnorePanel = null;
  }

  public String getHelpTopic() {
    return getId();
  }

  @Nullable
  private EditLocationDialog.NameLocationPair addExtLocation() {
    EditLocationDialog dialog = new EditLocationDialog(null, true);
    dialog.show();
    if (!dialog.isOK()) return null;
    setModified(true);
    return dialog.getPair();
  }

  @Nullable
  private EditLocationDialog.NameLocationPair editExtLocation(Object o) {
    EditLocationDialog dialog = new EditLocationDialog(null, true);
    final EditLocationDialog.NameLocationPair pair = (EditLocationDialog.NameLocationPair)o;
    dialog.init(pair);
    dialog.show();
    if (!dialog.isOK()) {
      return null;
    }
    setModified(true);
    return dialog.getPair();
  }

  @Nullable
  private String addIgnoreLocation() {
    EditLocationDialog dialog = new EditLocationDialog(null, false);
    dialog.show();
    if (!dialog.isOK()) return null;
    setModified(true);
    return dialog.getPair().myName;
  }

  @Nullable
  private String editIgnoreLocation(Object o) {
    EditLocationDialog dialog = new EditLocationDialog(null, false);
    dialog.init(new EditLocationDialog.NameLocationPair(o.toString(), null, false));
    dialog.show();
    if (!dialog.isOK()) return null;
    setModified(true);
    return dialog.getPair().myName;
  }

  public void selectResource(final String uri) {
    myExtPanel.setSelected(new EditLocationDialog.NameLocationPair(uri, null, false));
  }

  private static class PathRenderer extends DefaultTableCellRenderer {
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      final Component rendererComponent = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      if (value != null) {
        String loc = value.toString().replace('\\', '/');
        final int jarDelimIndex = loc.indexOf(JarFileSystem.JAR_SEPARATOR);
        final VirtualFile path;

        if (jarDelimIndex != -1) {
          path = JarFileSystem.getInstance().findFileByPath(loc);
        } else {
          path = LocalFileSystem.getInstance().findFileByPath(loc);
        }

        setForeground(path != null ? isSelected ? UIUtil.getTableSelectionForeground() : Color.black : new Color(210, 0, 0));
      }
      return rendererComponent;
    }
  }

  private static class IgnoredUrlsModel extends AddEditRemovePanel.TableModel<String> {
    private final String[] myNames = {XmlBundle.message("column.name.edit.external.resource.uri")};

    public int getColumnCount() {
      return myNames.length;
    }

    public Object getField(String o, int columnIndex) {
      return o;
    }

    public Class getColumnClass(int columnIndex) {
      return String.class;
    }

    public boolean isEditable(int column) {
      return false; 
    }

    public void setValue(Object aValue, String data, int columnIndex) {

    }

    public String getColumnName(int column) {
      return myNames[column];
    }
  }

  private static class ExtUrlsTableModel extends AddEditRemovePanel.TableModel<EditLocationDialog.NameLocationPair> {
    final String[] myNames =
      {XmlBundle.message("column.name.edit.external.resource.uri"), XmlBundle.message("column.name.edit.external.resource.location"), "Project"};

    public int getColumnCount() {
      return myNames.length;
    }

    public Object getField(EditLocationDialog.NameLocationPair pair, int columnIndex) {
      switch (columnIndex) {
        case 0:
          return pair.myName;
        case 1:
          return pair.myLocation;
        case 2:
          return !pair.myShared;
      }

      return "";
    }

    public Class getColumnClass(int columnIndex) {
      return columnIndex == 2 ? Boolean.class : String.class;
    }

    public boolean isEditable(int column) {
      return column == 2;
    }

    public void setValue(Object aValue, EditLocationDialog.NameLocationPair data, int columnIndex) {
      data.myShared = !((Boolean)aValue).booleanValue();
    }

    public String getColumnName(int column) {
      return myNames[column];
    }
  }

  public String getId() {
    return "preferences.externalResources";
  }

  @Nullable
  public Runnable enableSearch(String option) {
    return null;
  }

  public boolean needDisplay() {
    return !"Ruby".equals(System.getProperty("idea.platform.prefix"));
  }
}
