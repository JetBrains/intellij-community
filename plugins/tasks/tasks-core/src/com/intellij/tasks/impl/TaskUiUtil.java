package com.intellij.tasks.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * @author Mikhail Golubev
 */
public class TaskUiUtil {

  private static Logger LOG = Logger.getInstance(TaskUiUtil.class);

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
    private final ModalityState myModalityState = ModalityState.current();

    /**
     * Should be called only from EDT, so current modality state can be captured.
     */
    protected RemoteFetchTask(@Nullable Project project, @NotNull String title) {
      super(project, title);
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
     * which will prevent UI updating in modal dialog (e.g. in {@link com.intellij.tasks.config.TaskRepositoryEditor}).
     * @return
     */
    @Nullable
    @Override
    public final NotificationInfo notifyFinished() {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          updateUI();
        }
      }, myModalityState);
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
  public static abstract class ComboBoxUpdater<T> extends RemoteFetchTask<List<T>> {
    protected final ComboBox myComboBox;

    public ComboBoxUpdater(@Nullable Project project, @NotNull String title, @NotNull ComboBox comboBox) {
      super(project, title);
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
     *
     * @return selected combo box item
     */
    @Nullable
    public T getSelectedItem() {
      return getExtraItem();
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void updateUI() {
      if (myResult != null) {
        myComboBox.setModel(new DefaultComboBoxModel(ArrayUtil.toObjectArray(myResult)));
        T extra = getExtraItem();
        if (extra != null) {
          myComboBox.insertItemAt(extra, 0);
        }
        // ensure that selected ItemEvent will be fired, even if first item of the model
        // is the same as the next selected
        myComboBox.setSelectedItem(null);

        T selected = getSelectedItem();
        if (selected != null) {
          myComboBox.setSelectedItem(selected);
        }
        else if (myComboBox.getItemCount() > 0) {
          myComboBox.setSelectedIndex(0);
        }
      }
      else {
        // Some error occurred
        handleError();
      }
    }

    protected void handleError() {
      myComboBox.removeAllItems();
    }
  }

  /**
   * Very simple wrapper around {@link com.intellij.ui.ListCellRendererWrapper} useful for
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
