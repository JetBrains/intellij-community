package com.intellij.ide.plugins;

import java.util.Arrays;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: Dec 26, 2003
 * Time: 3:51:58 PM
 * To change this template use Options | File Templates.
 */
public class InstalledPluginsTableModel extends PluginTableModel<PluginDescriptor> {
  public InstalledPluginsTableModel(SortableProvider sortableProvider) {
    super(new PluginManagerColumnInfo [] {
      new PluginManagerColumnInfo(PluginManagerColumnInfo.COLUMN_NAME, sortableProvider),
      new PluginManagerColumnInfo(PluginManagerColumnInfo.COLUMN_VERSION, sortableProvider),
      new PluginManagerColumnInfo(PluginManagerColumnInfo.COLUMN_STATE, sortableProvider)
    }, sortableProvider);

    view = Arrays.asList(PluginManager.getPlugins());
    sortByColumn(sortableProvider.getSortColumn());
  }
}
