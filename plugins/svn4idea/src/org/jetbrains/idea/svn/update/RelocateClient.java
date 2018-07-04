// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.update;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.api.SvnClient;
import org.jetbrains.idea.svn.api.Url;

import java.io.File;

public interface RelocateClient extends SvnClient {

  void relocate(@NotNull File copyRoot, @NotNull Url fromPrefix, @NotNull Url toPrefix) throws VcsException;
}
