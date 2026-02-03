// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server;

public abstract class RemoteServerUtil {
  public static boolean isWSL(){
    return Boolean.parseBoolean(System.getProperty("idea.maven.wsl"));
  }

  public static boolean isDebug(String[] args){
    for (String arg : args) {
      if ("runWithDebugger".equals(arg)) {
        return true;
      }
    }
    return false;
  }
}
