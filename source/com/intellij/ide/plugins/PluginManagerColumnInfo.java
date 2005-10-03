package com.intellij.ide.plugins;

import com.intellij.openapi.util.IconLoader;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.SortableColumnModel;
import com.intellij.ide.IdeBundle;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.text.DateFormat;
import java.util.Comparator;
import java.util.Date;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: Dec 11, 2003
 * Time: 2:55:50 PM
 * To change this template use Options | File Templates.
 */
class PluginManagerColumnInfo extends ColumnInfo {
  public static final int COLUMN_NAME = 0;
  public static final int COLUMN_STATUS = 1;
  public static final int COLUMN_INSTALLED_VERSION = 2;
  public static final int COLUMN_VERSION = 3;
  public static final int COLUMN_DATE = 4;
  public static final int COLUMN_SIZE = 5;
  public static final int COLUMN_DOWNLOADS = 6;
  public static final int COLUMN_CATEGORY = 7;
  public static final int COLUMN_STATE = 8;

  public static final String [] COLUMNS = {
    IdeBundle.message("column.plugins.name"),
    IdeBundle.message("column.plugins.status"),
    IdeBundle.message("column.plugins.installed"),
    IdeBundle.message("column.plugins.version"),
    IdeBundle.message("column.plugins.date"),
    IdeBundle.message("column.plugins.size"),
    IdeBundle.message("column.plugins.downloads"),
    IdeBundle.message("column.plugins.category"),
    IdeBundle.message("column.plugins.state")};
  public static final int [] PREFERRED_WIDTH = {300, 80, 80, 80, 80, 80, 80, 80, 80};

  private int columnIdx;
  private SortableProvider mySortableProvider;

  public PluginManagerColumnInfo(int columnIdx, SortableProvider sortableProvider) {
    super(COLUMNS [columnIdx]);
    this.columnIdx = columnIdx;
    mySortableProvider = sortableProvider;
  }

  public Object valueOf(Object o) {
    if (o instanceof CategoryNode) {
      switch (columnIdx) {
        case COLUMN_NAME:
          return ((CategoryNode)o).getName();
        default:
          return "";
      }
    } else if (o instanceof PluginNode) {
      PluginNode plugin = ((PluginNode)o);
      switch (columnIdx) {
        case COLUMN_NAME:
          return plugin.getName();
        case COLUMN_INSTALLED_VERSION:
          PluginDescriptor existing = PluginManager.getPlugin(plugin.getId());
          if (existing == null)
            return IdeBundle.message("plugin.info.not.available");
          else
            return existing.getVersion();
        case COLUMN_VERSION:
          if (plugin.getVersion() == null)
            return IdeBundle.message("plugin.info.not.available");
          else
            return plugin.getVersion();
        case COLUMN_STATUS:
          return PluginNode.STATUS_NAMES[getRealNodeState(plugin)];
        case COLUMN_SIZE:
          final String size = plugin.getSize();
          if (size.equals("-1")) {
            return IdeBundle.message("plugin.info.unknown");
          }
          return size;
        case COLUMN_DOWNLOADS:
          return plugin.getDownloads();
        case COLUMN_DATE:
          if (plugin.getDate() != null)
            return DateFormat.getDateInstance(DateFormat.MEDIUM).format(
              new Date (Long.valueOf(plugin.getDate()).longValue()));
          else
            return IdeBundle.message("plugin.info.not.available");
        case COLUMN_CATEGORY:
          return ((CategoryNode)plugin.getParent()).getName();
        default:
          return "?";
      }
    } else if (o instanceof PluginDescriptor) {
      switch(columnIdx) {
        case COLUMN_NAME:
          return ((PluginDescriptor) o).getName();
        case COLUMN_VERSION:
        case COLUMN_INSTALLED_VERSION:
          return ((PluginDescriptor) o).getVersion();
        case COLUMN_STATE:
          return ((PluginDescriptor) o).isDeleted() ?
                 IdeBundle.message("status.plugin.will.be.removed.after.restart") :
                 IdeBundle.message("status.plugin.installed");
        default:
          return "?";
      }
    } else
      return null;
  }

  public static int compareVersion (String v1, String v2) {
    if (v1 == null && v2 == null)
      return 0;
    else if (v1 == null && v2 != null)
      return -1;
    else if (v1 != null && v2 == null)
      return 1;

    String [] part1 = v1.split("\\.");
    String [] part2 = v2.split("\\.");

    int idx = 0;
    for (; idx < part1.length && idx < part2.length; idx++) {
      String p1 = part1[idx];
      String p2 = part2[idx];

      int cmp;
      //noinspection HardCodedStringLiteral
      if (p1.matches("\\d+") && p2.matches("\\d+")) {
        cmp = new Integer(p1).compareTo(new Integer(p2));
      } else {
        cmp = part1 [idx].compareTo(part2[idx]);
      }
      if (cmp != 0)
        return cmp;
    }

    if (part1.length == part2.length)
      return 0;
    else if (part1.length > idx)
      return 1;
    else
      return -1;
  }

  public Comparator getComparator() {
    switch (columnIdx) {
      case COLUMN_NAME:
        return new Comparator() {
          public int compare(Object o1, Object o2) {
            if (o1 instanceof PluginNode && o2 instanceof PluginNode) {
              PluginNode p1 = (PluginNode)(mySortableProvider.getSortOrder() == SortableColumnModel.SORT_ASCENDING ? (PluginNode)o1 : o2);
              PluginNode p2 = (PluginNode)(mySortableProvider.getSortOrder() == SortableColumnModel.SORT_ASCENDING ? (PluginNode)o2 : o1);

              return p1.getName().compareToIgnoreCase(p2.getName());
            } else if (o1 instanceof PluginDescriptor && o2 instanceof PluginDescriptor) {
              PluginDescriptor p1 = (PluginDescriptor)(mySortableProvider.getSortOrder() == SortableColumnModel.SORT_ASCENDING ? (PluginDescriptor)o1 : o2);
              PluginDescriptor p2 = (PluginDescriptor)(mySortableProvider.getSortOrder() == SortableColumnModel.SORT_ASCENDING ? (PluginDescriptor)o2 : o1);

              return p1.getName().compareToIgnoreCase(p2.getName());
            } else
              return 0;
          }
        };
      case COLUMN_INSTALLED_VERSION:
        return new Comparator() {
          public int compare(Object o1, Object o2) {
            if (o1 instanceof PluginNode && o2 instanceof PluginNode) {
              PluginNode p1 = (PluginNode)(mySortableProvider.getSortOrder() == SortableColumnModel.SORT_ASCENDING ? (PluginNode)o1 : o2);
              PluginNode p2 = (PluginNode)(mySortableProvider.getSortOrder() == SortableColumnModel.SORT_ASCENDING ? (PluginNode)o2 : o1);

              PluginDescriptor pd1 = PluginManager.getPlugin(p1.getId());
              PluginDescriptor pd2 = PluginManager.getPlugin(p2.getId());

              if (pd1 == null && pd2 == null)
                return 0;
              else if (pd1 != null && pd2 == null)
                return 1;
              else if (pd1 == null && pd2 != null)
                return -1;
              else if (pd1 != null && pd2 != null)
                return compareVersion(pd1.getVersion(), pd2.getVersion());

              return compareVersion(p1.getVersion(), p2.getVersion());
            } else
              return 0;
          }
        };
      case COLUMN_VERSION:
        return new Comparator() {
          public int compare(Object o1, Object o2) {
            if (o1 instanceof PluginNode && o2 instanceof PluginNode) {
              PluginNode p1 = (PluginNode)(mySortableProvider.getSortOrder() == SortableColumnModel.SORT_ASCENDING ? (PluginNode)o1 : o2);
              PluginNode p2 = (PluginNode)(mySortableProvider.getSortOrder() == SortableColumnModel.SORT_ASCENDING ? (PluginNode)o2 : o1);

              return compareVersion(p1.getVersion(), p2.getVersion());
            } else if (o1 instanceof PluginDescriptor && o2 instanceof PluginDescriptor) {
              PluginDescriptor p1 = (PluginDescriptor)(mySortableProvider.getSortOrder() == SortableColumnModel.SORT_ASCENDING ? (PluginDescriptor)o1 : o2);
              PluginDescriptor p2 = (PluginDescriptor)(mySortableProvider.getSortOrder() == SortableColumnModel.SORT_ASCENDING ? (PluginDescriptor)o2 : o1);

              return compareVersion(p1.getVersion(), p2.getVersion());
            } else
              return 0;
          }
        };
      case COLUMN_STATUS:
        return new Comparator() {
          public int compare(Object o1, Object o2) {
            if (o1 instanceof PluginNode && o2 instanceof PluginNode) {
              PluginNode p1 = (PluginNode)(mySortableProvider.getSortOrder() == SortableColumnModel.SORT_ASCENDING ? (PluginNode)o1 : o2);
              PluginNode p2 = (PluginNode)(mySortableProvider.getSortOrder() == SortableColumnModel.SORT_ASCENDING ? (PluginNode)o2 : o1);

              return PluginNode.getStatusName(PluginManagerColumnInfo.getRealNodeState(p1)).compareTo(
                PluginNode.getStatusName(PluginManagerColumnInfo.getRealNodeState(p2)));
            } else
              return 0;
          }
        };
      case COLUMN_SIZE:
        return new Comparator() {
          public int compare(Object o1, Object o2) {
            if (o1 instanceof PluginNode && o2 instanceof PluginNode) {
              PluginNode p1 = (PluginNode)(mySortableProvider.getSortOrder() == SortableColumnModel.SORT_ASCENDING ? (PluginNode)o1 : o2);
              PluginNode p2 = (PluginNode)(mySortableProvider.getSortOrder() == SortableColumnModel.SORT_ASCENDING ? (PluginNode)o2 : o1);

              return new Long (p1.getSize()).compareTo(new Long(p2.getSize()));
            } else
              return 0;
          }
        };
      case COLUMN_DOWNLOADS:
        return new Comparator() {
          public int compare(Object o1, Object o2) {
            if (o1 instanceof PluginNode && o2 instanceof PluginNode) {
              PluginNode p1 = (PluginNode)(mySortableProvider.getSortOrder() == SortableColumnModel.SORT_ASCENDING ? (PluginNode)o1 : o2);
              PluginNode p2 = (PluginNode)(mySortableProvider.getSortOrder() == SortableColumnModel.SORT_ASCENDING ? (PluginNode)o2 : o1);

              return new Long (p1.getDownloads()).compareTo(new Long (p2.getDownloads()));
            } else
              return 0;
          }
        };
      case COLUMN_DATE:
        return new Comparator() {
          public int compare(Object o1, Object o2) {
            if (o1 instanceof PluginNode && o2 instanceof PluginNode) {
              PluginNode p1 = (PluginNode)(mySortableProvider.getSortOrder() == SortableColumnModel.SORT_ASCENDING ? (PluginNode)o1 : o2);
              PluginNode p2 = (PluginNode)(mySortableProvider.getSortOrder() == SortableColumnModel.SORT_ASCENDING ? (PluginNode)o2 : o1);

              if (p1.getDate() != null && p2.getDate() != null)
                return new Long (p1.getDate()).compareTo(new Long (p2.getDate()));
              else if (p1.getDate() != null && p2.getDate() == null)
                return 1;
              else if (p1.getDate() == null && p2.getDate() != null)
                return -1;
              else if (p1.getDate() == null && p2.getDate() == null)
                return 0;
              else
                return 0;
            } else
              return 0;
          }
        };
      case COLUMN_CATEGORY:
        return new Comparator() {
          public int compare(Object o1, Object o2) {
            if (o1 instanceof PluginNode && o2 instanceof PluginNode) {
              PluginNode p1 = (PluginNode)(mySortableProvider.getSortOrder() == SortableColumnModel.SORT_ASCENDING ? (PluginNode)o1 : o2);
              PluginNode p2 = (PluginNode)(mySortableProvider.getSortOrder() == SortableColumnModel.SORT_ASCENDING ? (PluginNode)o2 : o1);

              return p1.getParent().getName().compareToIgnoreCase(p2.getParent().getName());
            } else
              return 0;
          }
        };
      case COLUMN_STATE:
        return new Comparator() {
          public int compare(Object o1, Object o2) {
            if (o1 instanceof PluginDescriptor && o2 instanceof PluginDescriptor) {
              PluginDescriptor p1 = (PluginDescriptor)(mySortableProvider.getSortOrder() == SortableColumnModel.SORT_ASCENDING ? (PluginDescriptor)o1 : o2);
              PluginDescriptor p2 = (PluginDescriptor)(mySortableProvider.getSortOrder() == SortableColumnModel.SORT_ASCENDING ? (PluginDescriptor)o2 : o1);

              if (p1.isDeleted() == p2.isDeleted())
                return 0;
              else if (p1.isDeleted() && ! p2.isDeleted())
                return -1;
              else
                return 1;
            } else
              return 0;
          }
        };
      default:
        return new Comparator () {
          public int compare(Object o, Object o1) {
            return 0;
          }
        };
    }
  }

  public static int getRealNodeState (PluginNode node) {
    if (node.getStatus() == PluginNode.STATUS_DOWNLOADED)
      return PluginNode.STATUS_DOWNLOADED;
    else if (node.getStatus() == PluginNode.STATUS_DELETED)
      return PluginNode.STATUS_DELETED;
    else if (node.getStatus() == PluginNode.STATUS_CART)
      return PluginNode.STATUS_CART;

    PluginDescriptor existing = PluginManager.getPlugin(node.getId());

    if (existing == null)
      return PluginNode.STATUS_MISSING;

    if (existing.getVendor() == null ? true : !existing.getVendor().equals(node.getVendor())){
      return PluginNode.STATUS_MISSING;
    }
    
    int state = compareVersion(node.getVersion(), existing.getVersion());

    if (state == 0)
      return PluginNode.STATUS_CURRENT;
    else if (state >= 1)
      return PluginNode.STATUS_OUT_OF_DATE;
    else if (state <= -1) {
      // version of installed plugin is more then version of repository plugin
      // That status should be appeared on Plugin Developer's IDEA
      return PluginNode.STATUS_NEWEST;
    }

    return PluginNode.STATUS_UNKNOWN;
  }

  private static final Color RED_COLOR = new Color (255, 231, 227);
  private static final Color GREEN_COLOR = new Color (232, 243, 221);

  public TableCellRenderer getRenderer(Object o) {
    return new DefaultTableCellRenderer () {
      public Component getTableCellRendererComponent(JTable table,
                                                     Object value,
                                                     boolean isSelected,
                                                     boolean hasFocus,
                                                     int row,
                                                     int column) {

        if (column == COLUMN_NAME) {
          // add icon
          setIcon(IconLoader.getIcon("/nodes/plugin.png"));
        }

        if (! isSelected) {
          if (table.getModel() instanceof InstalledPluginsTableModel) {
            PluginDescriptor descriptor = ((PluginTable<PluginDescriptor>)table).getObjectAt(row);
            if (descriptor.isDeleted())
              setBackground(Color.lightGray);
            else
              setBackground(Color.white);
          } else if (table.getModel() instanceof AvailablePluginsTableModel) {
            PluginNode node = ((PluginTable<PluginNode>)table).getObjectAt(row);

            switch (PluginManagerColumnInfo.getRealNodeState(node)) {
              case PluginNode.STATUS_OUT_OF_DATE:
                setBackground(RED_COLOR);
                break;
              case PluginNode.STATUS_CURRENT:
                setBackground(GREEN_COLOR);
                break;
              default:
                setBackground(Color.white);
            }
          }
        }

        return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      }
    };
  }

  public Class getColumnClass() {
    switch (columnIdx) {
      case COLUMN_NAME:
      case COLUMN_STATUS:
      case COLUMN_INSTALLED_VERSION:
      case COLUMN_VERSION:
      case COLUMN_DATE:
      case COLUMN_CATEGORY:
      case COLUMN_STATE:
        return String.class;
      case COLUMN_SIZE:
      case COLUMN_DOWNLOADS:
        return Integer.class;
      default:
        return String.class;
    }
  }
}
