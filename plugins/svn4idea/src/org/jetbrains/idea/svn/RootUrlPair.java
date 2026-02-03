// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.api.Url;

public interface RootUrlPair {
  @NotNull
  VirtualFile getVirtualFile();

  @NotNull
  Url getUrl();
}
