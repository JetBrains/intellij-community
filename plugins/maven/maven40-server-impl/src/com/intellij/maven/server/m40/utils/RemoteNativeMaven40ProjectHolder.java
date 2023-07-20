// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.server.m40.utils;

import org.apache.maven.project.MavenProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.server.NativeMavenProjectHolder;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

public class RemoteNativeMaven40ProjectHolder implements NativeMavenProjectHolder {
  private static final Map<Integer, Reference<RemoteNativeMaven40ProjectHolder>> myMap = new HashMap<>();

  private final MavenProject myMavenProject;

  public RemoteNativeMaven40ProjectHolder(@NotNull MavenProject mavenProject) {
    myMavenProject = mavenProject;
    myMap.put(getId(), new WeakReference<>(this));
  }

  @Override
  public int getId() {
    return System.identityHashCode(this);
  }

  @NotNull
  public static MavenProject findProjectById(int id) {
    Reference<RemoteNativeMaven40ProjectHolder> reference = myMap.get(id);
    RemoteNativeMaven40ProjectHolder result = reference == null ? null : reference.get();
    if (result == null) {
      throw new RuntimeException("NativeMavenProjectHolder not found for id: " + id);
    }
    return result.myMavenProject;
  }
}