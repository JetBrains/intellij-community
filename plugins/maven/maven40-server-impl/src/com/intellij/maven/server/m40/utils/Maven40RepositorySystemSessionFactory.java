// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.server.m40.utils;

import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.resolver.RepositorySystemSessionFactory;
import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.util.graph.manager.DependencyManagerUtils;
import org.eclipse.aether.util.graph.transformer.ConflictResolver;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenWorkspaceMap;
import org.jetbrains.idea.maven.server.MavenServerConsoleIndicatorImpl;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class Maven40RepositorySystemSessionFactory implements RepositorySystemSessionFactory {
  private final RepositorySystemSessionFactory mySystemSessionFactory;
  private final MavenWorkspaceMap myWorkspaceMap;
  private final MavenServerConsoleIndicatorImpl myIndicator;

  public Maven40RepositorySystemSessionFactory(RepositorySystemSessionFactory systemSessionFactory,
                                               MavenWorkspaceMap map,
                                               MavenServerConsoleIndicatorImpl indicator) {
    mySystemSessionFactory = systemSessionFactory;
    myWorkspaceMap = map;
    myIndicator = indicator;
  }

  @Override
  public RepositorySystemSession.SessionBuilder newRepositorySessionBuilder(MavenExecutionRequest request) {
    RepositorySystemSession.SessionBuilder builder = mySystemSessionFactory.newRepositorySessionBuilder(request);
    builder
      .setWorkspaceReader(new Maven40WorkspaceMapReader(myWorkspaceMap))
      .setTransferListener(new Maven40TransferListenerAdapter(myIndicator))
      .setConfigProperty(ConflictResolver.CONFIG_PROP_VERBOSE, true)
      .setConfigProperty(DependencyManagerUtils.CONFIG_PROP_VERBOSE, true)
      .setConfigProperty(ConfigurationProperties.USER_AGENT, getUserAgent(builder));
    return builder;
  }

  private String getUserAgent(RepositorySystemSession.SessionBuilder builder) {
    String mavenUA = tryToGetMavenUserAgent();
    String version = System.getProperty("idea.version");
    StringBuilder result = new StringBuilder();
    if (mavenUA != null) {
      result.append(mavenUA).append(";");
    }
    if (version != null) {
      result.append("Intellij IDEA (").append(version).append(")");
    }
    else {
      result.append("Intellij IDEA");
    }
    return result.toString();
  }

  private @Nullable String tryToGetMavenUserAgent() {
    try {
      Method m = mySystemSessionFactory.getClass().getDeclaredMethod("getUserAgent");
      m.setAccessible(true);
      Object invoke = m.invoke(mySystemSessionFactory);
      if (invoke instanceof String) return (String)invoke;
      return null;
    }
    catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignore) {
      return null;
    }
  }
}
