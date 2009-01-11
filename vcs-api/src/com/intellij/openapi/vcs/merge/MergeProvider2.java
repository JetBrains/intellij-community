package com.intellij.openapi.vcs.merge;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.ColumnInfo;

/**
 * Merge provider which allows plugging into the functionality of the Multiple File Merge dialog.
 *
 * @author yole
 * @since 8.1
 */
public interface MergeProvider2 extends MergeProvider {
  enum Resolution { Merged, AcceptedYours, AcceptedTheirs }

  /**
   * Returns the list of additional columns to be displayed in the dialog. The Item type for the
   * column should be VirtualFile.
   *
   * @return the list of columns, or an empty list if no additional columns should be displayed.
   */
  ColumnInfo[] getMergeInfoColumns();

  /**
   * Returns true if a merge operation can be invoked for the specified virtual file, false otherwise.
   *
   * @param file a file shown in the dialog.
   * @return true if the merge dialog can be shown, false otherwise.
   */
  boolean canMerge(VirtualFile file);

  /**
   * Called when the user executes one of the resolve actions (merge, accept yours, accept theirs) for
   * a conflicting file.
   *
   * @param file the conflicting file.
   * @param resolution the used resolution.
   */
  void conflictResolvedForFile(VirtualFile file, Resolution resolution);
}
