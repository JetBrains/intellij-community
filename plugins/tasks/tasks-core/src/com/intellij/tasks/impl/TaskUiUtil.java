package com.intellij.tasks.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.tasks.config.TaskRepositoryEditor;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;

/**
 * @author Mikhail Golubev
 */
public class TaskUiUtil {

  private static final Logger LOG = Logger.getInstance(TaskUiUtil.class);

  private TaskUiUtil() {
    // Utility class
  }

  /**
   * Special kind of backgroundable task tailored to update UI in modal dialogs, which is very common for
   * task repository editors in settings.
   */
  public abstract static class RemoteFetchTask<T> extends Task.Backgroundable {
    protected T myResult;
    protected Exception myException;
    private final ModalityState myModalityState;

    /**
     * Should be called only from EDT, so current modality state can be captured.
     */
    protected RemoteFetchTask(@Nullable Project project, @NotNull String title) {
      this(project, title, ModalityState.current());
    }

    protected RemoteFetchTask(@Nullable Project project, @NotNull String title, @NotNull ModalityState modalityState) {
      super(project, title);
      myModalityState = modalityState;
    }

    @Override
    public final void run(@NotNull ProgressIndicator indicator) {
      try {
        myResult = fetch(indicator);
      }
      catch (Exception e) {
        LOG.error(e);
        myException = e;
      }
    }

    /**
     * {@link #onSuccess()} can't be used for this purpose, because it doesn't consider current modality state
     * which will prevent UI updating in modal dialog (e.g. in {@link TaskRepositoryEditor}).
     */
    @Nullable
    @Override
    public final NotificationInfo notifyFinished() {
      ApplicationManager.getApplication().invokeLater(() -> updateUI(), myModalityState);
      return null;
    }

    @NotNull
    protected abstract T fetch(@NotNull ProgressIndicator indicator) throws Exception;

    protected abstract void updateUI();
  }

  /**
   * Auxiliary remote fetcher designed to simplify updating of combo boxes in repository editors, which is
   * indeed a rather common task.
   */
  public static abstract class ComboBoxUpdater<T> extends RemoteFetchTask<Collection<T>> {
    protected final JComboBox myComboBox;

    public ComboBoxUpdater(@Nullable Project project, @NotNull String title, @NotNull JComboBox comboBox) {
      super(project, title, ModalityState.any());
      myComboBox = comboBox;
    }

    /**
     * Return extra item like "All projects", which will be added as first item after every combo box update.
     *
     * @return extra first combo box item
     */
    @Nullable
    public T getExtraItem() {
      return null;
    }

    /**
     * Return item to select after every combo box update. Default implementation select item, returned by {@link #getExtraItem()}.
     * If returned value is not present in the list it will be added depending on policy set by {@link #addSelectedItemIfMissing()}.
     *
     * @return selected combo box item
     * @see #addSelectedItemIfMissing()
     */
    @Nullable
    public T getSelectedItem() {
      return getExtraItem();
    }

    /**
     * @return whether value returned by {@link #getSelectedItem()} should be forcibly added to the combo box.
     */
    protected boolean addSelectedItemIfMissing() {
      return false;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void updateUI() {
      if (myResult != null) {
        myComboBox.setModel(new DefaultComboBoxModel(ArrayUtil.toObjectArray(myResult)));
        final T extra = getExtraItem();
        if (extra != null) {
          myComboBox.insertItemAt(extra, 0);
        }
        // ensure that selected ItemEvent will be fired, even if first item of the model
        // is the same as the next selected
        myComboBox.setSelectedItem(null);

        final T selected = getSelectedItem();
        if (selected != null) {
          if (!selected.equals(extra) && !myResult.contains(selected)) {
            if (addSelectedItemIfMissing()) {
              myComboBox.addItem(selected);
              myComboBox.setSelectedItem(selected);
            }
            else {
              selectFirstItem();
            }
          }
          else {
            myComboBox.setSelectedItem(selected);
          }
        }
        else {
          selectFirstItem();
        }
      }
      else {
        handleError();
      }
    }

    private void selectFirstItem() {
      if (myComboBox.getItemCount() > 0) {
        myComboBox.setSelectedIndex(0);
      }
    }

    protected void handleError() {
      myComboBox.removeAllItems();
    }
  }

  /**
   * Very simple wrapper around {@link ListCellRendererWrapper} useful for
   * combo boxes where each item has plain text representation with special message for
   * {@code null} value.
   */
  public static class SimpleComboBoxRenderer<T> extends ListCellRendererWrapper<T> {
    private final String myNullDescription;
    public SimpleComboBoxRenderer(@NotNull String nullDescription) {
      myNullDescription = nullDescription;
    }

    @Override
    public final void customize(JList list, T value, int index, boolean selected, boolean hasFocus) {
      setText(value == null ? myNullDescription : getDescription(value));
    }

    @NotNull
    protected String getDescription(@NotNull T item) {
      return item.toString();
    }
  }
}
