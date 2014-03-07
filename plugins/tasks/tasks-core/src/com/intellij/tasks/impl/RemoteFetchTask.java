package com.intellij.tasks.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * @author Mikhail Golubev
 */
public abstract class RemoteFetchTask<T> extends Task.Backgroundable {
  protected List<T> myObjects;
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
      myObjects = fetch(indicator);
    }
    catch (Exception e) {
      myException = e;
    }
  }

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
  protected abstract List<T> fetch(@NotNull ProgressIndicator indicator) throws Exception;

  protected abstract void updateUI();

  /**
   * Auxiliary remote fetcher designed to simplify updating of combo boxes in repository editors, which is
   * indeed a rather common task.
   */
  public static abstract class ComboBoxUpdater<T> extends RemoteFetchTask<T> {
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
      if (myObjects != null) {
        myComboBox.setModel(new DefaultComboBoxModel(ArrayUtil.toObjectArray(myObjects)));
        T extra = getExtraItem();
        if (extra != null) {
          myComboBox.insertItemAt(extra, 0);
        }
        T selected = getSelectedItem();
        if (selected != null) {
          myComboBox.setSelectedItem(selected);
        }
      }
      else {
        // Some error occurred
        myComboBox.removeAllItems();
      }
    }
  }
}
