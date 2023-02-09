package org.jetbrains.idea.svn.change;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.SvnClient;

import java.io.File;

public interface ChangeListClient extends SvnClient {

  void add(@NotNull String changeList, @NotNull File path, String @Nullable [] changeListsToOperate) throws VcsException;

  void remove(@NotNull File path) throws VcsException;
}
