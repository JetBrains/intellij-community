// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.status.Status;

public interface StatusReceiver {
  void process(@NotNull FilePath path, Status status) throws SvnBindException;

  default void processIgnored(@NotNull FilePath path) {
  }

  default void processUnversioned(@NotNull FilePath path) {
  }

  void processCopyRoot(@NotNull VirtualFile file, @Nullable Url url,
                       @NotNull WorkingCopyFormat format, @Nullable Url rootURL);

  void bewareRoot(@NotNull VirtualFile vf, Url url);

  void finish();
}
