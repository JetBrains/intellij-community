package com.intellij.openapi.vcs.merge;

import com.intellij.util.ui.ColumnInfo;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * Represents the state of a multiple file merge operation.
 *
 * @author yole
 * @since 8.1
 * @see com.intellij.openapi.vcs.merge.MergeProvider2#createMergeSession
 */
public interface MergeSession {
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

}
