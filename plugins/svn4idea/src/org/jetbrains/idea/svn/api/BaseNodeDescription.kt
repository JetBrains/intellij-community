// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.api;

import org.jetbrains.annotations.NotNull;

public abstract class BaseNodeDescription {

  @NotNull private final NodeKind myNodeKind;

  protected BaseNodeDescription(@NotNull NodeKind nodeKind) {
    myNodeKind = nodeKind;
  }

  @NotNull
  public NodeKind getNodeKind() {
    return myNodeKind;
  }

  public boolean isFile() {
    return myNodeKind.isFile();
  }

  public boolean isDirectory() {
    return myNodeKind.isDirectory();
  }

  public boolean isNone() {
    return myNodeKind.isNone();
  }
}
