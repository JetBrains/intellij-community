package com.intellij.tasks.timetracking;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.IconLoader;
import com.intellij.tasks.LocalTask;
import com.intellij.tasks.TaskListenerAdapter;
import com.intellij.tasks.TaskManager;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.table.TableView;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Comparator;

/**
 * User: evgeny.zakrevsky
 * Date: 11/8/12
 */
public class TasksToolWindowPanel extends JPanel implements Disposable {

  private Timer myTimer;
  private final ListTableModel<LocalTask> myTableModel;
  private final TaskManager myTaskManager;

  public TasksToolWindowPanel(final Project project) {
    super(new BorderLayout());
    myTaskManager = TaskManager.getManager(project);

    final TableView<LocalTask> table = new TableView<LocalTask>(createListModel());
    myTableModel = table.getListTableModel();
    updateTable();

    add(ScrollPaneFactory.createScrollPane(table, true), BorderLayout.CENTER);

    myTaskManager.addTaskListener(new TaskListenerAdapter() {
      @Override
      public void taskDeactivated(final LocalTask task) {
        updateTable();
      }

      @Override
      public void taskActivated(final LocalTask task) {
        updateTable();
      }

      @Override
      public void taskAdded(final LocalTask task) {
        updateTable();
      }

      @Override
      public void taskRemoved(final LocalTask task) {
        updateTable();
      }
    });

    myTimer = new Timer(1000, new ActionListener() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        table.repaint();
      }
    });
    myTimer.start();
  }

  private void updateTable() {
    myTableModel.setItems(ContainerUtil.filter(myTaskManager.getLocalTasks(), new Condition<LocalTask>() {
      @Override
      public boolean value(final LocalTask task) {
        return task.isActive() || task.getTimeSpent() != 0;
      }
    }));
  }

  private ListTableModel<LocalTask> createListModel() {
    final ColumnInfo<LocalTask, String> task = new ColumnInfo<LocalTask, String>("Task") {

      @Nullable
      @Override
      public String valueOf(final LocalTask task) {
        return task.getPresentableName();
      }

      @Nullable
      @Override
      public TableCellRenderer getRenderer(final LocalTask task) {
        return new TableCellRenderer() {
          @Override
          public Component getTableCellRendererComponent(final JTable table,
                                                         final Object value,
                                                         final boolean isSelected,
                                                         final boolean hasFocus,
                                                         final int row,
                                                         final int column) {
            JPanel panel = new JPanel(new BorderLayout());
            panel.setBackground(UIUtil.getTableBackground(isSelected));
            final SimpleColoredComponent component = new SimpleColoredComponent();
            final boolean isClosed = task.isClosed() || myTaskManager.isLocallyClosed(task);
            component.append((String)value, getAttributes(isClosed, task.isActive(), isSelected));
            component.setIcon(isClosed ? IconLoader.getTransparentIcon(task.getIcon()) : task.getIcon());
            component.setIconOpaque(false);
            component.setOpaque(false);
            panel.add(component, BorderLayout.CENTER);
            panel.setOpaque(true);
            return panel;
          }
        };
      }

      @Nullable
      @Override
      public Comparator<LocalTask> getComparator() {
        return new Comparator<LocalTask>() {
          public int compare(LocalTask o1, LocalTask o2) {
            int i = Comparing.compare(o2.getUpdated(), o1.getUpdated());
            return i == 0 ? Comparing.compare(o2.getCreated(), o1.getCreated()) : i;
          }
        };
      }
    };

    final ColumnInfo<LocalTask, String> spentTime = new ColumnInfo<LocalTask, String>("Time Spent") {
      @Nullable
      @Override
      public String valueOf(final LocalTask task) {
        long timeSpent = task.getTimeSpent();
        if (task.isActive()) {
          timeSpent += System.currentTimeMillis() - task.getActivated();
          return formatDuration(timeSpent);
        }
        return DateFormatUtil.formatDuration(timeSpent);
      }

      @Nullable
      @Override
      public TableCellRenderer getRenderer(final LocalTask task) {
        return new TableCellRenderer() {
          @Override
          public Component getTableCellRendererComponent(final JTable table,
                                                         final Object value,
                                                         final boolean isSelected,
                                                         final boolean hasFocus,
                                                         final int row,
                                                         final int column) {
            JPanel panel = new JPanel(new BorderLayout());
            panel.setBackground(UIUtil.getTableBackground(isSelected));
            final SimpleColoredComponent component = new SimpleColoredComponent();
            component
              .append((String)value, getAttributes(task.isClosed() || myTaskManager.isLocallyClosed(task), task.isActive(), isSelected));
            component.setOpaque(false);
            panel.add(component, BorderLayout.CENTER);
            panel.setOpaque(true);
            return panel;
          }
        };
      }

      @Nullable
      @Override
      public Comparator<LocalTask> getComparator() {
        return new Comparator<LocalTask>() {
          @Override
          public int compare(final LocalTask o1, final LocalTask o2) {
            return Comparing.compare(o1.getTimeSpent(), o2.getTimeSpent());
          }
        };
      }
    };

    return new ListTableModel<LocalTask>((new ColumnInfo[]{task, spentTime}));
  }

  private static SimpleTextAttributes getAttributes(final boolean isClosed, final boolean isActive, final boolean isSelected) {
    return new SimpleTextAttributes(isActive ? SimpleTextAttributes.STYLE_BOLD : SimpleTextAttributes.STYLE_PLAIN,
                                    isSelected
                                    ? UIUtil.getTableSelectionForeground()
                                    : isClosed ? UIUtil.getLabelDisabledForeground() : UIUtil.getTableForeground());
  }

  private static String formatDuration(final long milliseconds) {
    final int second = 1000;
    final int minute = 60 * second;
    final int hour = 60 * minute;
    final int day = 24 * hour;
    final int days = (int)milliseconds / day;
    String daysString = days != 0 ? days + "d " : "";

    return daysString + String.format("%d:%02d:%02d", milliseconds % day / hour, milliseconds % hour / minute,
                                      milliseconds % minute / second);
  }

  @Override
  public void dispose() {
    myTimer.stop();
    myTimer = null;
  }
}
