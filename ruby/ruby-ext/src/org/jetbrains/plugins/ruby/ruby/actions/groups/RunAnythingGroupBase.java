package org.jetbrains.plugins.ruby.ruby.actions.groups;

import com.intellij.psi.codeStyle.NameUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ruby.ruby.actions.RunAnythingItem;
import org.jetbrains.plugins.ruby.ruby.actions.RunAnythingSearchListModel;

public abstract class RunAnythingGroupBase extends RunAnythingGroup {
  /**
   * Adds limited number of matched items into the list.
   *
   * @param model           needed to avoid adding duplicates into the list
   * @param pattern         input search string
   * @param textToMatch     an item presentation text to be matched with
   * @param isInsertionMode if true gets {@link #getMaxItemsToInsert()} group items, else limits to {@link #getMaxInitialItems()}
   * @return true if limit exceeded
   */
  boolean addToList(@NotNull RunAnythingSearchListModel model,
                    @NotNull SearchResult result,
                    @NotNull String pattern,
                    @NotNull RunAnythingItem item,
                    @NotNull String textToMatch,
                    boolean isInsertionMode) {
    if (!model.contains(item) && NameUtil.buildMatcher("*" + pattern).build().matches(textToMatch)) {
      if (result.size() == (isInsertionMode ? getMaxItemsToInsert() : getMaxInitialItems())) {
        result.setNeedMore(true);
        return true;
      }
      result.add(item);
    }
    return false;
  }
}