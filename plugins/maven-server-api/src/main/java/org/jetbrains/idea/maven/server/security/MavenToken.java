// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server.security;

import java.io.Serializable;
import java.util.UUID;

public class MavenToken implements Serializable {
  private final UUID myUUID;

  public MavenToken(String str) {myUUID = UUID.fromString(str);}

  @Override
  public String toString() {
    return myUUID.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    MavenToken token = (MavenToken)o;

    return myUUID.equals(token.myUUID);
  }

  @Override
  public int hashCode() {
    return myUUID.hashCode();
  }
}
