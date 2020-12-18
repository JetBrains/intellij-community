// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server;

public abstract class RemoteServerUtil {
  public static boolean isKnownPortRequired(){
    return Boolean.parseBoolean(System.getProperty("idea.maven.knownPort"));
  }

  public static boolean isWSL(){
    return Boolean.parseBoolean(System.getProperty("idea.maven.wsl"));
  }
}
