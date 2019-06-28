// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.onlinecompletion;

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
