// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.completion;

import org.jetbrains.idea.maven.model.MavenCoordinate;

public final class MavenScopeTable {


  private MavenScopeTable() {}

  public static String getUsualScope(MavenCoordinate item) {
    String groupId = item.getGroupId();
    if (groupId == null) {
      return null;
    }
    if (groupId.contains("junit") || groupId.equals("org.mockito") || groupId.equals("org.hamcrest")) {
      return "test";
    }
    return null;
  }
}
