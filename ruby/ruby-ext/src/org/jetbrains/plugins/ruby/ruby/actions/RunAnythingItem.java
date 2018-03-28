package org.jetbrains.plugins.ruby.ruby.actions;

import com.intellij.execution.Executor;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * {@link RunAnythingItem} represents an item of 'Run Anything' list
 *
 * @param <T> user object of the item
 */
public abstract class RunAnythingItem<T> {
  /**
   * Returns user object of current item
   */
  @NotNull
  public abstract T getValue();

  /**
   * Returns text presentation of {@link T}
   */
  @NotNull
  public abstract String getText();

  /**
   * Returns icon of {@link T}
   */
  @NotNull
  public abstract Icon getIcon();

  /**
   * Returns current item {@link Component} presentation
   *
   * @param isSelected true if item is selected in the list
   */
  @NotNull
  public abstract Component getComponent(boolean isSelected);

  /**
   * Sends statistic if current item action is being executed
   */
  protected void triggerUsage() {}

  /**
   * Executes specific action on choosing current item in the list
   *
   * @param executor      Defines whether the action is being executed in debug mode or not
   * @param event         'Run Anything' action event
   * @param workDirectory Working directory where the action will be executed
   * @param focusOwner    Focus owner
   */
  public void run(@NotNull Project project,
                  @NotNull Executor executor,
                  @Nullable AnActionEvent event,
                  @Nullable VirtualFile workDirectory,
                  @Nullable Component focusOwner) {
    triggerUsage();
  }
}
