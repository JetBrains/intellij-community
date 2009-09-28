package org.jetbrains.idea.svn;

import com.intellij.openapi.vcs.changes.LocallyDeletedChange;
import com.intellij.openapi.vcs.FilePath;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class SvnLocallyDeletedChange extends LocallyDeletedChange {
  private final ConflictState myConflictState;

  public SvnLocallyDeletedChange(@NotNull final FilePath path, final ConflictState state) {
    super(path);
    myConflictState = state;
  }

  @Override
  public Icon getAddIcon() {
    return myConflictState.getIcon();
  }

  @Override
  public String getDescription() {
    final String description = myConflictState.getDescription();
    if (description != null) {
      return SvnBundle.message("svn.changeview.locally.deleted.item.in.conflict.text", description);
    }
    return description;
  }
}
