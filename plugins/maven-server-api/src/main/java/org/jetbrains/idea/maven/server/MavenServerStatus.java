// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server;

import java.io.Serializable;
import java.util.HashMap;

public class MavenServerStatus implements Serializable {

  public boolean statusCollected = false;

  //todo: replace with stacktraces
  public final HashMap<String, Integer> fileReadAccessCount = new HashMap<>();
  public final HashMap<String, Integer> pluginResolveCount = new HashMap<>();
}
