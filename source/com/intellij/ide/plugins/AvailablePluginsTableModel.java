package com.intellij.ide.plugins;

import java.util.ArrayList;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: Dec 10, 2003
 * Time: 7:26:23 PM
 * To change this template use Options | File Templates.
 */
public class AvailablePluginsTableModel extends PluginTableModel <PluginNode>{
  public static final int COLUMN_COUNT = PluginManagerColumnInfo.COLUMNS.length;

  public AvailablePluginsTableModel(CategoryNode root, SortableProvider sortableProvider) {
    super (new PluginManagerColumnInfo [] {
    new PluginManagerColumnInfo(PluginManagerColumnInfo.COLUMN_NAME, sortableProvider),
    new PluginManagerColumnInfo(PluginManagerColumnInfo.COLUMN_STATUS, sortableProvider),
    new PluginManagerColumnInfo(PluginManagerColumnInfo.COLUMN_INSTALLED_VERSION, sortableProvider),
    new PluginManagerColumnInfo(PluginManagerColumnInfo.COLUMN_VERSION, sortableProvider),
    new PluginManagerColumnInfo(PluginManagerColumnInfo.COLUMN_DATE, sortableProvider),
    new PluginManagerColumnInfo(PluginManagerColumnInfo.COLUMN_SIZE, sortableProvider),
    new PluginManagerColumnInfo(PluginManagerColumnInfo.COLUMN_DOWNLOADS, sortableProvider),
    new PluginManagerColumnInfo(PluginManagerColumnInfo.COLUMN_CATEGORY, sortableProvider)
  }, sortableProvider);

    view = new ArrayList<PluginNode>();
    makeView (root);

    sortByColumn(sortableProvider.getSortColumn());
  }

  private void makeView(CategoryNode start) {
    if (start.getPlugins() != null && start.getPlugins().size() > 0) {
        view.addAll(start.getPlugins());
    }

    if (start.getChildren() != null && start.getChildren().size() > 0)
      for (int i = 0; i < start.getChildren().size(); i++) {
        CategoryNode categoryNode = start.getChildren().get(i);
        makeView(categoryNode);
      }
  }
}
