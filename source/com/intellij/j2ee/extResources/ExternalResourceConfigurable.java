package com.intellij.j2ee.extResources;

import com.intellij.ide.IdeBundle;
import com.intellij.j2ee.openapi.ex.ExternalResourceManagerEx;
import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ExternalResourceConfigurable extends BaseConfigurable implements SearchableConfigurable {
  private JPanel myPanel;
  private List<EditLocationDialog.NameLocationPair> myPairs;
  private List<String> myIgnoredUrls;
  private AddEditRemovePanel<EditLocationDialog.NameLocationPair> myExtPanel;
  private AddEditRemovePanel<String> myIgnorePanel;

  public String getDisplayName() {
    return IdeBundle.message("display.name.edit.external.resource");
  }

  public JComponent createComponent() {
    myPanel = new JPanel(new GridBagLayout()) {
      public Dimension getPreferredSize() {
        return new Dimension(700, 400);
      }
    };

    myExtPanel = new AddEditRemovePanel<EditLocationDialog.NameLocationPair>(
      IdeBundle.message("label.edit.external.resource.configure.external.resources"), new ExtUrlsTableModel(), myPairs) {
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

      protected char getAddMnemonic() {
        return 'A';
      }

      protected char getEditMnemonic() {
        return 'E';
      }

      protected char getRemoveMnemonic() {
        return 'R';
      }
    };

    myExtPanel.setRenderer(1, new PathRenderer());

    myIgnorePanel = new AddEditRemovePanel<String>(IdeBundle.message("label.edit.external.resource.configure.ignored.resources"),
                                                   new IgnoredUrlsModel(), myIgnoredUrls) {
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

      protected char getAddMnemonic() {
        return 'd';
      }

      protected char getEditMnemonic() {
        return 't';
      }

      protected char getRemoveMnemonic() {
        return 'm';
      }
    };

    myPanel.add(myExtPanel,
                new GridBagConstraints(0, 0, 1, 1, 1, 1, GridBagConstraints.NORTH, GridBagConstraints.BOTH, new Insets(5, 2, 4, 2), 0, 0));
    myPanel.add(myIgnorePanel,
                new GridBagConstraints(0, 1, 1, 1, 1, 1, GridBagConstraints.NORTH, GridBagConstraints.BOTH, new Insets(5, 2, 4, 2), 0, 0));


    return myPanel;
  }

  public Icon getIcon() {
    return IconLoader.getIcon("/general/configurableExternalResources.png");
  }

  public void apply() {
    ExternalResourceManagerEx manager = ExternalResourceManagerEx.getInstanceEx();

    manager.clearAllResources();
    for (Object myPair : myPairs) {
      EditLocationDialog.NameLocationPair pair = (EditLocationDialog.NameLocationPair)myPair;
      manager.addResource(pair.myName, pair.myLocation.replace('\\', '/'));
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
      String loc = manager.getResourceLocation(url);
      myPairs.add(new EditLocationDialog.NameLocationPair(url, loc));
    }

    Collections.sort(myPairs);

    myIgnoredUrls = new ArrayList<String>();
    final String[] ignoredResources = manager.getIgnoredResources();
    for (String ignoredResource : ignoredResources) {
      myIgnoredUrls.add(ignoredResource);
    }

    Collections.sort(myIgnoredUrls);

    myExtPanel.setData(myPairs);
    myIgnorePanel.setData(myIgnoredUrls);

    setModified(false);
  }

  public void disposeUIResources() {
    myPanel = null;
    myExtPanel = null;
    myIgnorePanel = null;
  }

  public String getHelpTopic() {
    return "preferences.externalResources";
  }

  private EditLocationDialog.NameLocationPair addExtLocation() {
    EditLocationDialog dialog = new EditLocationDialog(null, true);
    dialog.show();
    if (!dialog.isOK()) return null;
    setModified(true);
    return dialog.getPair();
  }

  private EditLocationDialog.NameLocationPair editExtLocation(Object o) {
    EditLocationDialog dialog = new EditLocationDialog(null, true);
    final EditLocationDialog.NameLocationPair pair = (EditLocationDialog.NameLocationPair)o;
    dialog.init(pair.myName, pair.myLocation);
    dialog.show();
    if (!dialog.isOK()) return null;
    setModified(true);
    return dialog.getPair();
  }

  private String addIgnoreLocation() {
    EditLocationDialog dialog = new EditLocationDialog(null, false);
    dialog.show();
    if (!dialog.isOK()) return null;
    setModified(true);
    return dialog.getPair().myName;
  }

  private String editIgnoreLocation(Object o) {
    EditLocationDialog dialog = new EditLocationDialog(null, false);
    dialog.init(o.toString(), o.toString());
    dialog.show();
    if (!dialog.isOK()) return null;
    setModified(true);
    return dialog.getPair().myName;
  }

  public void editUri() {
    myExtPanel.doEdit();
  }

  public void selectResource(final String uri) {
    myExtPanel.setSelected(new EditLocationDialog.NameLocationPair(uri, null));
  }

  private class PathRenderer extends DefaultTableCellRenderer {
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      String loc = value.toString().replace('\\', '/');
      final int jarDelimIndex = loc.indexOf(JarFileSystem.JAR_SEPARATOR);
      final VirtualFile path;

      if (jarDelimIndex != -1) {
        path = JarFileSystem.getInstance().findFileByPath(loc);
      } else {
        path = LocalFileSystem.getInstance().findFileByPath(loc);
      }

      setForeground(path != null ? isSelected ? UIUtil.getTableSelectionForeground() : Color.black : new Color(210, 0, 0));
      return this;
    }
  }

  private class IgnoredUrlsModel implements AddEditRemovePanel.TableModel {
    private final String[] myNames = {IdeBundle.message("column.name.edit.external.resource.uri")};

    public int getColumnCount() {
      return myNames.length;
    }

    public Object getField(Object o, int columnIndex) {
      return o;
    }

    public String getColumnName(int column) {
      return myNames[column];
    }
  }

  private class ExtUrlsTableModel implements AddEditRemovePanel.TableModel {
    final String[] myNames =
      {IdeBundle.message("column.name.edit.external.resource.uri"), IdeBundle.message("column.name.edit.external.resource.location")};

    public int getColumnCount() {
      return myNames.length;
    }

    public Object getField(Object o, int columnIndex) {
      final EditLocationDialog.NameLocationPair pair = (EditLocationDialog.NameLocationPair)o;
      switch (columnIndex) {
        case 0:
          return pair.myName;
        case 1:
          return pair.myLocation;
      }

      return "";
    }

    public String getColumnName(int column) {
      return myNames[column];
    }
  }


  public String getId() {
    return getHelpTopic();
  }

  public boolean clearSearch() {
    return false;
  }

  @Nullable
  public Runnable enableSearch(String option) {
    return null;
  }
}
