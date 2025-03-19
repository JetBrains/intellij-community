// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.java.jps;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.jps.incremental.dependencies.DependencyAuthenticationDataProvider;

import java.util.Arrays;
import java.util.List;

public final class SpaceDependencyAuthenticationDataProvider extends DependencyAuthenticationDataProvider {
  private static final List<String> PROVIDED_HOSTS = Arrays.asList("jetbrains.team", "jetbrains.space");

  @Override
  public AuthenticationData provideAuthenticationData(String url) {
    if (!ContainerUtil.exists(PROVIDED_HOSTS, it -> url.contains(it))) {
      return null;
    }

    String userName = System.getProperty("jps.auth.spaceUsername");
    String password = System.getProperty("jps.auth.spacePassword");
    if (userName != null && password != null) {
      return new AuthenticationData(userName, password);
    }
    return null;
  }
}
