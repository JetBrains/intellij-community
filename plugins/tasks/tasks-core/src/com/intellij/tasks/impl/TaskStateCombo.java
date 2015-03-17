package com.intellij.tasks.impl;

import com.intellij.ide.actions.TemplateKindCombo;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Trinity;
import com.intellij.tasks.CustomTaskState;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.impl.TaskUiUtil.ComboBoxUpdater;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.Function;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.List;

/**
 * @author Mikhail Golubev
 */
public abstract class TaskStateCombo extends JPanel {
  private static final CustomTaskState DO_NOT_UPDATE_STATE = new CustomTaskState("", "-- do not update --");

  public static boolean isStateSupportedFor(@Nullable Task task) {
    if (task == null || !task.isIssue()) {
      return false;
    }
    final TaskRepository repository = task.getRepository();
    return repository != null && repository.isSupported(TaskRepository.STATE_UPDATING);
  }

  private final Project myProject;
  private final Task myTask;
  private final TemplateKindCombo myKindCombo = new TemplateKindCombo();

  // For designer only
  @SuppressWarnings("unused")
  public TaskStateCombo() {
    this(null, null);
  }

  @SuppressWarnings({"GtkPreferredJComboBoxRenderer", "unchecked"})
  public TaskStateCombo(Project project, Task task) {
    myProject = project;
    myTask = task;

    final JBLabel hintButton = new JBLabel();
    hintButton.setIcon(PlatformIcons.UP_DOWN_ARROWS);
    hintButton.setToolTipText("Pressing Up or Down arrows while in editor changes the state");
    final JComboBox comboBox = myKindCombo.getComboBox();
    comboBox.setPreferredSize(new Dimension(300, UIUtil.fixComboBoxHeight(comboBox.getPreferredSize().height)));
    final ListCellRenderer defaultRenderer = comboBox.getRenderer();
    comboBox.setRenderer(new ListCellRenderer() {
      @SuppressWarnings({"unchecked", "GtkPreferredJComboBoxRenderer"})
      @Override
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        if (value == null) {
          return new ListCellRendererWrapper<CustomStateTrinityAdapter>() {
            @Override
            public void customize(JList list, CustomStateTrinityAdapter value, int index, boolean selected, boolean hasFocus) {
              setText("-- no states available --");
            }
          }.getListCellRendererComponent(list, null, index, isSelected, cellHasFocus);
        }
        return defaultRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      }
    });

    setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));
    add(myKindCombo);
    add(hintButton);
  }

  public boolean scheduleUpdate() {
    if (myProject != null && isStateSupportedFor(myTask)) {
      final JComboBox comboBox = myKindCombo.getComboBox();
      final TaskRepository repository = myTask.getRepository();
      assert repository != null;
      new ComboBoxUpdater<CustomStateTrinityAdapter>(myProject, "Fetching available task states...", comboBox) {
        @NotNull
        @Override
        protected List<CustomStateTrinityAdapter> fetch(@NotNull ProgressIndicator indicator) throws Exception {
          return CustomStateTrinityAdapter.wrapList(repository.getAvailableTaskStates(myTask));
        }

        @Nullable
        @Override
        public CustomStateTrinityAdapter getSelectedItem() {
          final CustomTaskState state = getPreferredState(repository, CustomStateTrinityAdapter.unwrapList(myResult));
          return state != null ? new CustomStateTrinityAdapter(state) : null;
        }

        @Nullable
        @Override
        public CustomStateTrinityAdapter getExtraItem() {
          return new CustomStateTrinityAdapter(DO_NOT_UPDATE_STATE);
        }
      }.queue();
      return true;
    }
    return false;
  }

  /**
   * @return {@code null} if no state is available at the moment or special "do not update" state was selected
   */
  @Nullable
  public CustomTaskState getSelectedState() {
    final CustomStateTrinityAdapter item = (CustomStateTrinityAdapter)myKindCombo.getComboBox().getSelectedItem();
    if (item == null) {
      return null;
    }
    final CustomTaskState state = item.myState;
    return state == DO_NOT_UPDATE_STATE ? null : state;
  }

  public void registerUpDownAction(@NotNull JComponent focusable) {
    myKindCombo.registerUpDownHint(focusable);
  }

  @NotNull
  public JComboBox getComboBox() {
    return myKindCombo.getComboBox();
  }

  /**
   * Determine what state should be initially selected in the list.
   * @param repository task repository to communicate with
   * @param available  tasks states already downloaded from the repository
   * @return task state to select
   */
  @Nullable
  protected abstract CustomTaskState getPreferredState(@NotNull TaskRepository repository, @NotNull Collection<CustomTaskState> available);

  private static class CustomStateTrinityAdapter extends Trinity<String, Icon, String> {
    final CustomTaskState myState;

    public CustomStateTrinityAdapter(@NotNull CustomTaskState state) {
      super(state.getPresentableName(), null, state.getId());
      myState = state;
    }

    @NotNull
    static List<CustomStateTrinityAdapter> wrapList(@NotNull Collection<CustomTaskState> states) {
      return ContainerUtil.map(states, new Function<CustomTaskState, CustomStateTrinityAdapter>() {
        @Override
        public CustomStateTrinityAdapter fun(CustomTaskState state) {
          return new CustomStateTrinityAdapter(state);
        }
      });
    }

    @NotNull
    static List<CustomTaskState> unwrapList(@NotNull Collection<CustomStateTrinityAdapter> wrapped) {
      return ContainerUtil.map(wrapped, new Function<CustomStateTrinityAdapter, CustomTaskState>() {
        @Override
        public CustomTaskState fun(CustomStateTrinityAdapter adapter) {
          return adapter.myState;
        }
      });
    }
  }
}
