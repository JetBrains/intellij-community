package org.jetbrains.plugins.ruby.ruby.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

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
   * Creates current item {@link Component}
   *
   * @param isSelected true if item is selected in the list
   */
  @NotNull
  public abstract Component createComponent(boolean isSelected);

  /**
   * Sends statistic if current item action is being executed
   */
  protected void triggerUsage() {}

  /**
   * Executes specific action on choosing current item in the list
   *
   * @param dataContext Use {@link DataContext} to extract focus owner component, original action event, working directory
   */
  public void run(@NotNull Project project, @NotNull DataContext dataContext) {
    triggerUsage();
  }
}
