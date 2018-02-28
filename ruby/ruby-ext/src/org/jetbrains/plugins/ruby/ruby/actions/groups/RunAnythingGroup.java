package org.jetbrains.plugins.ruby.ruby.actions.groups;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.codeStyle.NameUtil;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.ruby.actions.RunAnythingAction.SearchResult;
import org.jetbrains.plugins.ruby.ruby.actions.RunAnythingItem;
import org.jetbrains.plugins.ruby.ruby.actions.RunAnythingSearchListModel;

import javax.swing.*;
import java.util.Arrays;

/**
 * Represents 'run anything' list group. See {@link RunAnythingCommandGroup} and {@link RunAnythingRunConfigurationGroup} as examples.
 */
public abstract class RunAnythingGroup {
  public static final ExtensionPointName<RunAnythingGroup> EP_NAME =
    ExtensionPointName.create("org.jetbrains.plugins.ruby.runAnythingGroup");

  private static final int DEFAULT_MORE_STEP_COUNT = 5;
  private volatile int moreIndex = -1;
  private volatile int titleIndex = -1;

  /**
   * @return Current group title in the main list.
   */
  @NotNull
  public abstract String getTitle();

  /**
   * @return Unique settings key to store current group visibility property.
   */
  @NotNull
  protected abstract String getSettingsKey();

  /**
   * @return Current group maximum number of items to be shown.
   */
  protected abstract int getMaxItemsToShow();

  /**
   * Gets current group items to add into the main list.
   */
  protected abstract SearchResult getItems(@NotNull Project project,
                                           @Nullable Module module,
                                           @NotNull RunAnythingSearchListModel listModel,
                                           @NotNull String pattern,
                                           boolean isMore,
                                           @NotNull Runnable check);

  /**
   * Defines whether this group should be shown with empty input or not.
   */
  public boolean isRecent() {
    return false;
  }

  /**
   * Adds current group matched items into the list.
   */
  public final synchronized void buildToList(@NotNull Project project,
                                             @Nullable Module module,
                                             @NotNull RunAnythingSearchListModel listModel,
                                             @NotNull String pattern,
                                             @NotNull Runnable check,
                                             @NotNull Computable<Boolean> isCanceled) {
    SearchResult result = getAllItems(project, module, listModel, pattern, false, check);

    check.run();
    if (result.size() > 0) {
      //noinspection SSBasedInspection
      SwingUtilities.invokeLater(() -> {
        if (isCanceled.compute()) return;

        titleIndex = listModel.size();
        for (Object file : result) {
          listModel.addElement(file);
        }
        moreIndex = result.needMore ? listModel.getSize() - 1 : -1;
      });
    }
  }

  /**
   * Adds limited number of matched items into the list.
   *
   * @return true if limit exceeded
   */
  boolean addToList(@NotNull RunAnythingSearchListModel listModel,
                    @NotNull SearchResult result,
                    @NotNull String pattern,
                    @NotNull RunAnythingItem item,
                    @NotNull String textToMatch,
                    boolean isMore) {
    if (!listModel.contains(item) && NameUtil.buildMatcher("*" + pattern).build().matches(textToMatch)) {
      if (result.size() == (isMore ? DEFAULT_MORE_STEP_COUNT : getMaxItemsToShow())) {
        result.needMore = true;
        return true;
      }
      result.add(item);
    }
    return false;
  }

  /**
   * Gets all current group matched items if its visibility turned on and empty collection otherwise
   */
  public SearchResult getAllItems(@NotNull Project project,
                                  @Nullable Module module,
                                  @NotNull RunAnythingSearchListModel listModel,
                                  @NotNull String pattern,
                                  boolean isMore,
                                  @NotNull Runnable check) {
    return !Registry.is(getSettingsKey()) ? new SearchResult() : getItems(project, module, listModel, pattern, isMore, check);
  }

  public void dropMoreIndex() {
    moreIndex = -1;
  }

  private static void shiftMoreIndex(int index, int shift) {
    Arrays.stream(EP_NAME.getExtensions()).filter(runAnythingGroup -> runAnythingGroup.moreIndex >= index)
          .forEach(runAnythingGroup -> runAnythingGroup.moreIndex += shift);
  }

  public static String getTitle(int index) {
    return Arrays.stream(EP_NAME.getExtensions()).filter(runAnythingGroup -> index == runAnythingGroup.titleIndex).findFirst()
                 .map(RunAnythingGroup::getTitle).orElse(null);
  }

  private static void shift(int index, int shift) {
    Arrays.stream(EP_NAME.getExtensions())
          .filter(runAnythingGroup -> runAnythingGroup.titleIndex != -1 && runAnythingGroup.titleIndex > index)
          .forEach(runAnythingGroup -> runAnythingGroup.titleIndex += shift);
  }

  public static void clearMoreIndex() {
    Arrays.stream(EP_NAME.getExtensions()).forEach(runAnythingGroup -> runAnythingGroup.moreIndex = -1);
  }

  private static void clearTitleIndex() {
    Arrays.stream(EP_NAME.getExtensions()).forEach(runAnythingGroup -> runAnythingGroup.titleIndex = -1);
  }

  public static int[] getAllIndexes() {
    TIntArrayList list = new TIntArrayList();
    for (RunAnythingGroup runAnythingGroup : EP_NAME.getExtensions()) {
      list.add(runAnythingGroup.titleIndex);
    }
    for (RunAnythingGroup runAnythingGroup : EP_NAME.getExtensions()) {
      list.add(runAnythingGroup.moreIndex);
    }

    return list.toNativeArray();
  }

  @Nullable
  public static RunAnythingGroup findRunAnythingGroup(int index) {
    return Arrays.stream(EP_NAME.getExtensions()).filter(runAnythingGroup -> index == runAnythingGroup.moreIndex).findFirst().orElse(null);
  }

  public static boolean isMoreIndex(int index) {
    return Arrays.stream(EP_NAME.getExtensions()).anyMatch(runAnythingGroup -> runAnythingGroup.moreIndex == index);
  }

  public static void shiftIndexes(int baseIndex, int shift) {
    shift(baseIndex, shift);
    shiftMoreIndex(baseIndex, shift);
  }

  public static void clearIndexes() {
    clearTitleIndex();
    clearMoreIndex();
  }
}