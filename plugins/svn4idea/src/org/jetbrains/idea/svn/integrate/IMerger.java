// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.integrate;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

import static org.jetbrains.annotations.Nls.Capitalization.Sentence;

public interface IMerger {
  boolean hasNext();

  void mergeNext() throws VcsException;

  @Nls(capitalization = Sentence) @Nullable String getInfo();

  @Nls(capitalization = Sentence) @Nullable String getSkipped();

  @Nls(capitalization = Sentence) @NotNull String getComment();

  @Nullable
  File getMergeInfoHolder();

  void afterProcessing();
}
