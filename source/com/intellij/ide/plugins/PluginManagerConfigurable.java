package com.intellij.ide.plugins;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.*;
import com.intellij.util.ui.SortableColumnModel;
import org.jdom.Element;

import javax.swing.*;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: Oct 26, 2003
 * Time: 9:30:44 PM
 * To change this template use Options | File Templates.
 */
public class PluginManagerConfigurable extends BaseConfigurable implements JDOMExternalizable, ApplicationComponent {
  public int AVAILABLE_SORT_COLUMN = 0;
  public int INSTALLED_SORT_COLUMN = 0;
  public int CART_SORT_COLUMN = 0;
  public int AVAILABLE_SORT_COLUMN_ORDER = SortableColumnModel.SORT_ASCENDING;
  public int INSTALLED_SORT_COLUMN_ORDER = SortableColumnModel.SORT_ASCENDING;
  public int CART_SORT_COLUMN_ORDER = SortableColumnModel.SORT_ASCENDING;

  public boolean EXPANDED = false;
  public String FIND = "";
  public boolean TREE_VIEW = false;

  private PluginManagerMain myPluginManagerMain;

  public static PluginManagerConfigurable getInstance() {
    return ApplicationManager.getApplication().getComponent(PluginManagerConfigurable.class);
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

  public String getComponentName() {
    return "PluginManagerConfigurable";
  }

  public void initComponent() { }

  public void disposeComponent() {
  }

  public String getDisplayName() {
    return "Plugins";
  }

  public void reset() {
    //To change body of implemented methods use Options | File Templates.
  }

  public String getHelpTopic() {
    return "preferences.pluginManager";
  }

  public void disposeUIResources() {
    myPluginManagerMain = null;
  }

  public JComponent createComponent() {
    if (myPluginManagerMain == null) {
      myPluginManagerMain = new PluginManagerMain(new MyAvailableProvider(), new MyInstalledProvider(), new MyCartProvider());
    }

    return myPluginManagerMain.getMainPanel();
  }

  public void apply() throws ConfigurationException {
    if (myPluginManagerMain.isRequireShutdown()) {
      if (Messages.showYesNoDialog("You need to shut down " + ApplicationNamesInfo.getInstance().getProductName() +
                                   " to activate changes in plugins. Would you like do it now?", "Plugins", Messages.getQuestionIcon()) == 0) {
        ApplicationManagerEx.getApplicationEx().exit(true);        
      }
      else {
        myPluginManagerMain.ignoreChages ();
      }
    }
  }

  public boolean isModified() {
    return myPluginManagerMain != null? myPluginManagerMain.isRequireShutdown() : false;
  }

  public Icon getIcon() {
    return IconLoader.getIcon("/general/pluginManager.png");
  }

  private class MyAvailableProvider implements SortableProvider {
    public int getSortOrder() {
      return AVAILABLE_SORT_COLUMN_ORDER;
    }

    public int getSortColumn() {
      return AVAILABLE_SORT_COLUMN;
    }

    public void setSortOrder(int sortOrder) {
      AVAILABLE_SORT_COLUMN_ORDER = sortOrder;
    }

    public void setSortColumn(int sortColumn) {
      AVAILABLE_SORT_COLUMN = sortColumn;
    }
  }

  private class MyInstalledProvider implements SortableProvider {
    public int getSortOrder() {
      return INSTALLED_SORT_COLUMN_ORDER;
    }

    public int getSortColumn() {
      return INSTALLED_SORT_COLUMN;
    }

    public void setSortOrder(int sortOrder) {
      INSTALLED_SORT_COLUMN_ORDER = sortOrder;
    }

    public void setSortColumn(int sortColumn) {
      INSTALLED_SORT_COLUMN = sortColumn;
    }
  }

  private class MyCartProvider implements SortableProvider {
    public int getSortOrder() {
      return CART_SORT_COLUMN_ORDER;
    }

    public int getSortColumn() {
      return CART_SORT_COLUMN;
    }

    public void setSortOrder(int sortOrder) {
      CART_SORT_COLUMN_ORDER = sortOrder;
    }

    public void setSortColumn(int sortColumn) {
      CART_SORT_COLUMN = sortColumn;
    }
  }
}
