package com.intellij.tasks.timetracking;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.IconLoader;
import com.intellij.tasks.LocalTask;
import com.intellij.tasks.TaskListenerAdapter;
import com.intellij.tasks.TaskManager;
import com.intellij.tasks.actions.GotoTaskAction;
import com.intellij.tasks.actions.SwitchTaskAction;
import com.intellij.tasks.impl.TaskManagerImpl;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.table.TableView;
import com.intellij.util.IconUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.UIUtil;
import icons.TasksIcons;
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
public class TasksToolWindowPanel extends SimpleToolWindowPanel implements Disposable {

  private final ListTableModel<LocalTask> myTableModel;
  private final TaskManager myTaskManager;
  private final Project myProject;
  private Timer myTimer;
  private final TableView<LocalTask> myTable;

  public TasksToolWindowPanel(final Project project, final boolean vertical) {
    super(vertical);
    myProject = project;
    myTaskManager = TaskManager.getManager(project);

    myTable = new TableView<LocalTask>(createListModel());
    myTableModel = myTable.getListTableModel();
    updateTable();

    setContent(ScrollPaneFactory.createScrollPane(myTable, true));
    setToolbar(createToolbar());

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

    myTimer = new Timer(TaskManagerImpl.TIME_TRACKING_TIME_UNIT, new ActionListener() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        myTable.repaint();
      }
    });
    myTimer.start();
  }

  private static SimpleTextAttributes getAttributes(final boolean isClosed, final boolean isRunning, final boolean isSelected) {
    return new SimpleTextAttributes(isRunning ? SimpleTextAttributes.STYLE_BOLD : SimpleTextAttributes.STYLE_PLAIN,
                                    isSelected
                                    ? UIUtil.getTableSelectionForeground()
                                    : isClosed && !isRunning ? UIUtil.getLabelDisabledForeground() : UIUtil.getTableForeground());
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

  private JComponent createToolbar() {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new GotoTaskAction());
    group.add(new AnAction("Remove Task", "Remove Task", IconUtil.getRemoveIcon()) {
      @Override
      public void actionPerformed(final AnActionEvent e) {
        for (LocalTask localTask : myTable.getSelectedObjects()) {
          SwitchTaskAction.removeTask(myProject, localTask, myTaskManager);
        }
      }
    });
    group.add(new ToggleAction("Hide closed tasks", "Hide closed tasks", AllIcons.Actions.Checked) {
      @Override
      public boolean isSelected(final AnActionEvent e) {
        return myTaskManager.isHideClosedTasks();
      }

      @Override
      public void setSelected(final AnActionEvent e, final boolean state) {
        myTaskManager.setHideClosedTasks(state);
      }
    });
    group.add(new ToggleAction("Auto mode", "Automatic starting and stopping of timer", TasksIcons.AutoMode) {
      @Override
      public boolean isSelected(final AnActionEvent e) {
        return myTaskManager.isTimeTrackingAutoMode();
      }

      @Override
      public void setSelected(final AnActionEvent e, final boolean state) {
        myTaskManager.setTimeTrackingAutoMode(state);
        updateTable();
      }
    });
    group.add(new AnAction() {
      @Override
          public void update(final AnActionEvent e) {
            if (myTaskManager.isTimeTrackingAutoMode()) {
              e.getPresentation().setEnabled(false);
              e.getPresentation().setIcon(TasksIcons.StartTimer);
              e.getPresentation().setText("Start timer for active task");
            }
            else {
              e.getPresentation().setEnabled(true);
              if (myTaskManager.getActiveTask().isRunning()) {
                e.getPresentation().setIcon(TasksIcons.StopTimer);
                e.getPresentation().setText("Stop timer for active task");
              }
              else {
                e.getPresentation().setIcon(TasksIcons.StartTimer);
                e.getPresentation().setText("Start timer for active task");
              }
            }
          }

          @Override
          public void actionPerformed(final AnActionEvent e) {
            final LocalTask activeTask = myTaskManager.getActiveTask();
            if (activeTask.isRunning()) {
              activeTask.setRunning(false);
            }
            else {
              activeTask.setRunning(true);
            }
          }
        });
    final ActionToolbar actionToolBar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, myVertical);
    return actionToolBar.getComponent();
  }

  private void updateTable() {
    myTableModel.setItems(ContainerUtil.filter(myTaskManager.getLocalTasks(myTaskManager.isHideClosedTasks()), new Condition<LocalTask>() {
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
            final boolean isRunning = myTaskManager.isTimeTrackingAutoMode() ? task.isActive() : task.isRunning();
            component.append((String)value, getAttributes(isClosed, isRunning, isSelected));
            component.setIcon(isRunning
                              ? LayeredIcon.create(task.getIcon(), AllIcons.General.Run)
                              : isClosed ? IconLoader.getTransparentIcon(task.getIcon()) : task.getIcon());
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
        if (myTaskManager.isTimeTrackingAutoMode() ? task.isActive() : task.isRunning()) {
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
            final boolean isClosed = task.isClosed() || myTaskManager.isLocallyClosed(task);
            final boolean isRunning = myTaskManager.isTimeTrackingAutoMode() ? task.isActive() : task.isRunning();
            component.append((String)value, getAttributes(isClosed, isRunning, isSelected));
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

  @Override
  public void dispose() {
    myTimer.stop();
    myTimer = null;
  }
}
