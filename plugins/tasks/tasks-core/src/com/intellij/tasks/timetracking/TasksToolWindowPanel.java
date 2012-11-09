package com.intellij.tasks.timetracking;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.tasks.LocalTask;
import com.intellij.tasks.TaskListenerAdapter;
import com.intellij.tasks.TaskManager;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.table.JBTable;
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

  public TasksToolWindowPanel(final Project project) {
    super(new BorderLayout());
    final TaskManager taskManager = TaskManager.getManager(project);

    final JTable table = new JBTable(createListModel());
    final ListTableModel<LocalTask> model = (ListTableModel<LocalTask>)table.getModel();
    model.setItems(taskManager.getLocalTasks());

    add(ScrollPaneFactory.createScrollPane(table, true), BorderLayout.CENTER);

    taskManager.addTaskListener(new TaskListenerAdapter() {
      @Override
      public void taskDeactivated(final LocalTask task) {
        model.setItems(taskManager.getLocalTasks());
      }

      @Override
      public void taskActivated(final LocalTask task) {
        model.setItems(taskManager.getLocalTasks());
      }

      @Override
      public void taskAdded(final LocalTask task) {
        model.setItems(taskManager.getLocalTasks());
      }

      @Override
      public void taskRemoved(final LocalTask task) {
        model.setItems(taskManager.getLocalTasks());
      }
    });

    myTimer = new Timer(60 * 1000, new ActionListener() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        table.repaint();
      }
    });
    myTimer.start();
  }

  private static ListTableModel<LocalTask> createListModel() {
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
            final SimpleColoredComponent component = new SimpleColoredComponent();
            component.setBackground(UIUtil.getTableBackground(isSelected));
            final boolean isClosed = task.isClosed() || task.isClosedLocally();
            component.setForeground(isClosed ? UIUtil.getLabelDisabledForeground() : UIUtil.getTableForeground(isSelected));
            component.setIcon(isClosed ? IconLoader.getTransparentIcon(task.getIcon()) : task.getIcon());
            return component;
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
        }
        return DateFormatUtil.formatDuration(timeSpent);
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

  private static String formatDuration(final long milliseconds) {
    final int second = 1000;
    final int minute = 60 * 1000;
    final int hour = 60 * 60 * 1000;

    return String.format("%d:%02d:%02d", milliseconds / hour, milliseconds % hour / minute,
                         milliseconds % minute / second);
  }

  @Override
  public void dispose() {
    myTimer.stop();
    myTimer = null;
  }
}
