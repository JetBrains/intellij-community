package com.intellij.j2ee.extResources;

import com.intellij.j2ee.openapi.ex.ExternalResourceManagerEx;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.LocalFileSystem;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;

public class ExternalResourceConfigurable extends BaseConfigurable implements ApplicationComponent {
  private JPanel myPanel;
  private ArrayList myPairs;
  private ArrayList myIgnoredUrls;
  private AddEditRemovePanel myExtPanel;
  private AddEditRemovePanel myIgnorePanel;

  public ExternalResourceConfigurable() {
  }

  public void disposeComponent() {
  }

  public void initComponent() { }

  public String getDisplayName() {
    return "Resources";
  }

  public JComponent createComponent() {
    myPanel = new JPanel(new GridBagLayout());

    myExtPanel = new AddEditRemovePanel("Configure External Resources:", new ExtUrlsTableModel(), myPairs) {
      protected Object addItem() {
        return addExtLocation();
      }

      protected boolean removeItem(Object o) {
        setModified(true);
        return true;
      }

      protected Object editItem(Object o) {
        return editExtLocation(o);
      }

      protected char getAddMnemonic(){
        return 'A';
      }

      protected char getEditMnemonic(){
        return 'E';
      }

      protected char getRemoveMnemonic(){
        return 'R';
      }
    };

    myExtPanel.setRenderer(1, new PathRenderer());

    myIgnorePanel = new AddEditRemovePanel("Configure Ignored Resources:", new IgnoredUrlsModel(), myIgnoredUrls) {
      protected Object addItem() {
        return addIgnoreLocation();
      }

      protected boolean removeItem(Object o) {
        setModified(true);
        return true;
      }

      protected Object editItem(Object o) {
        return editIgnoreLocation(o);
      }

      protected char getAddMnemonic(){
        return 'd';
      }

      protected char getEditMnemonic(){
        return 't';
      }

      protected char getRemoveMnemonic(){
        return 'm';
      }
    };

    myPanel.add(myExtPanel, new GridBagConstraints(0, 0, 1, 1, 1, 1, GridBagConstraints.NORTH, GridBagConstraints.BOTH, new Insets(5, 2, 4, 2), 0, 0));
    myPanel.add(myIgnorePanel, new GridBagConstraints(0, 1, 1, 1, 1, 1, GridBagConstraints.NORTH, GridBagConstraints.BOTH, new Insets(5, 2, 4, 2), 0, 0));

    return myPanel;
  }

  public Icon getIcon() {
    return IconLoader.getIcon("/general/configurableExternalResources.png");
  }

  public void apply() {
    ExternalResourceManagerEx manager = ExternalResourceManagerEx.getInstanceEx();

    manager.clearAllResources();
    for (int i = 0; i < myPairs.size(); i++) {
      Pair pair = (Pair)myPairs.get(i);
      manager.addResource(pair.myString1, pair.myString2.replace('\\', '/'));
    }

    for (int i = 0; i < myIgnoredUrls.size(); i++) {
      String url = (String)myIgnoredUrls.get(i);
      manager.addIgnoredResource(url);
    }
  }

  public void reset() {
    myPairs = new ArrayList();
    ExternalResourceManagerEx manager = ExternalResourceManagerEx.getInstanceEx();

    String[] urls = manager.getAvailableUrls();
    for (int i = 0; i < urls.length; i++) {
      String loc = manager.getResourceLocation(urls[i]);
      myPairs.add(new Pair(urls[i], loc));
    }

    Collections.sort(myPairs);

    myIgnoredUrls = new ArrayList();
    final String[] ignoredResources = manager.getIgnoredResources();
    for (int i = 0; i < ignoredResources.length; i++) {
      String ignoredResource = ignoredResources[i];
      myIgnoredUrls.add(ignoredResource);
    }

    Collections.sort(myIgnoredUrls);

    myExtPanel.setData(myPairs);
    myIgnorePanel.setData(myIgnoredUrls);

    setModified(false);
  }

  public void disposeUIResources() {
    myPanel = null;
  }

  public String getHelpTopic() {
    return "preferences.externalResources";
  }

  private Object addExtLocation() {
    EditLocationDialog dialog = new EditLocationDialog(null, true);
    dialog.show();
    if (!dialog.isOK()) return null;
    setModified(true);
    return dialog.getPair();
  }

  private Object editExtLocation(Object o) {
    EditLocationDialog dialog = new EditLocationDialog(null, true);
    dialog.init((Pair)o);
    dialog.show();
    if (!dialog.isOK()) return null;
    setModified(true);
    return dialog.getPair();
  }

  private Object addIgnoreLocation() {
    EditLocationDialog dialog = new EditLocationDialog(null, false);
    dialog.show();
    if (!dialog.isOK()) return null;
    setModified(true);
    return dialog.getPair().myString1;
  }

  private Object editIgnoreLocation(Object o) {
    EditLocationDialog dialog = new EditLocationDialog(null, false);
    dialog.init(new Pair(o.toString(), o.toString()));
    dialog.show();
    if (!dialog.isOK()) return null;
    setModified(true);
    return dialog.getPair().myString1;
  }


  public String getComponentName() {
    return "ExternalResourceConfigurable";
  }

  private class PathRenderer extends DefaultTableCellRenderer {
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      final String loc = value.toString().replace('\\', '/');
      setForeground(LocalFileSystem.getInstance().findFileByPath(loc) != null ? Color.black : new Color(210, 0, 0));
      return this;
    }
  }

  public static class Pair implements Comparable {
    String myString1;
    String myString2;

    public Pair(String string1, String string2) {
      myString1 = string1;
      myString2 = string2;
    }

    public int compareTo(Object o) {
      return myString1.compareTo(((Pair)o).myString1);
    }
  }

  private class IgnoredUrlsModel implements AddEditRemovePanel.TableModel {
    private final String[] myNames = {"URI"};

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
    final String[] myNames = {"URI", "Location"};

    public int getColumnCount() {
      return myNames.length;
    }

    public Object getField(Object o, int columnIndex) {
      final Pair pair = (Pair)o;
      switch (columnIndex) {
        case 0:
          return pair.myString1;
        case 1:
          return pair.myString2;
      }

      return "";
    }

    public String getColumnName(int column) {
      return myNames[column];
    }
  }
}
