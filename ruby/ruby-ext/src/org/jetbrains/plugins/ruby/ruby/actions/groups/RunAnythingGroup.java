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

public abstract class RunAnythingGroup {
  public static final ExtensionPointName<RunAnythingGroup> EP_NAME =
    ExtensionPointName.create("org.jetbrains.plugins.ruby.runAnythingGroup");

  private static final int DEFAULT_MORE_STEP_COUNT = 5;
  private volatile int moreIndex = -1;
  private volatile int titleIndex = -1;

  @NotNull
  protected abstract String getTitle();

  @NotNull
  protected abstract String getKey();

  protected abstract int getMax();

  public enum WidgetID {PERMANENT, RAKE, BUNDLER, TEMPORARY, GENERATORS, COMMANDS}

  @NotNull
  public abstract WidgetID getWidget();

  protected abstract SearchResult getItems(@NotNull Project project,
                                           @Nullable Module module,
                                           @NotNull RunAnythingSearchListModel listModel,
                                           @NotNull String pattern,
                                           boolean isMore,
                                           @NotNull Runnable check);

  public boolean isRecent() {
    return false;
  }

  public synchronized void buildToList(@NotNull Project project,
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

  boolean addToList(@NotNull RunAnythingSearchListModel listModel,
                    @NotNull SearchResult result,
                    @NotNull String pattern,
                    @NotNull RunAnythingItem item,
                    @NotNull String textToMatch,
                    boolean isMore) {
    if (!listModel.contains(item) && NameUtil.buildMatcher("*" + pattern).build().matches(textToMatch)) {
      if (result.size() == (isMore ? DEFAULT_MORE_STEP_COUNT : getMax())) {
        result.needMore = true;
        return true;
      }
      result.add(item);
    }
    return false;
  }

  public SearchResult getAllItems(@NotNull Project project,
                                  @Nullable Module module,
                                  @NotNull RunAnythingSearchListModel listModel,
                                  @NotNull String pattern,
                                  boolean isMore,
                                  @NotNull Runnable check) {
    return !Registry.is(getKey()) ? new SearchResult() : getItems(project, module, listModel, pattern, isMore, check);
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
    for (RunAnythingGroup runAnythingGroup : EP_NAME.getExtensions()) {
      runAnythingGroup.moreIndex = -1;
    }
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
  public static WidgetID findWidget(int index) {
    return Arrays.stream(EP_NAME.getExtensions()).filter(runAnythingGroup -> index == runAnythingGroup.moreIndex).findFirst()
                 .map(RunAnythingGroup::getWidget).orElse(null);
  }

  public static boolean isMoreIndex(int index) {
    return Arrays.stream(EP_NAME.getExtensions()).anyMatch(runAnythingGroup -> runAnythingGroup.moreIndex == index);
  }

  public static void shiftIndexes(int index, int shift) {
    shift(index, shift);
    shiftMoreIndex(index, shift);
  }

  public static void dropMoreIndex(@NotNull WidgetID id) {
    Arrays.stream(EP_NAME.getExtensions()).filter(runAnythingGroup -> runAnythingGroup.getWidget().equals(id))
          .forEach(runAnythingGroup -> runAnythingGroup.moreIndex = -1);
  }

  public static void clearIndexes() {
    clearTitleIndex();
    clearMoreIndex();
  }

  @Nullable
  public static RunAnythingGroup findGroup(@NotNull WidgetID id) {
    return Arrays.stream(EP_NAME.getExtensions()).filter(runAnythingGroup -> id.equals(runAnythingGroup.getWidget())).findFirst()
                 .orElse(null);
  }
}