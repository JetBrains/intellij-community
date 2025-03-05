// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.server.m40.utils;

import org.apache.maven.api.cli.InvokerRequest;
import org.apache.maven.cling.invoker.LookupContext;
import org.apache.maven.cling.invoker.ProtoLookup;
import org.apache.maven.cling.invoker.mvn.MavenContext;
import org.apache.maven.cling.invoker.mvn.MavenInvoker;
import org.apache.maven.cling.invoker.mvn.resident.ResidentMavenContext;
import org.apache.maven.cling.invoker.mvn.resident.ResidentMavenInvoker;
import org.apache.maven.execution.MavenExecutionRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.server.MavenServerGlobals;

public class Maven40Invoker extends ResidentMavenInvoker {
  ResidentMavenContext myContext = null;

  public Maven40Invoker(ProtoLookup protoLookup) {
    super(protoLookup);
  }

  @Override
  protected int doInvoke(ResidentMavenContext context) throws Exception {
    pushCoreProperties(context);
    validate(context);
    prepare(context);
    configureLogging(context);
    createTerminal(context);
    activateLogging(context);
    helpOrVersionAndMayExit(context);
    preCommands(context);
    tryRunAndRetryOnFailure(
      "container",
      () -> container(context),
      () -> ((Maven40InvokerRequest)context.invokerRequest).disableCoreExtensions()
    );
    postContainer(context);
    pushUserProperties(context);
    lookup(context);
    init(context);
    postCommands(context);
    tryRun(
      "settings",
      () -> settings(context),
      () -> context.localRepositoryPath = localRepositoryPath(context)
    );
    //return execute(context);
    myContext = context;
    return 0;
  }

  /**
   * adapted from {@link MavenInvoker#execute(MavenContext)}
   */
  public MavenExecutionRequest createMavenExecutionRequest() throws Exception {
    ResidentMavenContext context = myContext;
    MavenExecutionRequest request = prepareMavenExecutionRequest();
    toolchains(context, request);
    populateRequest(context, context.lookup, request);
    //return doExecute(context, request);
    return request;
  }

  private boolean tryRun(String methodName, ThrowingRunnable action, Runnable onFailure) {
    try {
      action.run();
    }
    catch (Exception e) {
      MavenServerGlobals.getLogger().warn("Maven40Invoker." + methodName + ": " + e.getMessage(), e);
      if (null != onFailure) {
        onFailure.run();
      }
      return false;
    }
    return true;
  }

  private void tryRunAndRetryOnFailure(String methodName, ThrowingRunnable action, Runnable onFailure) {
    if (!tryRun(methodName, action, onFailure)) {
      tryRun(methodName, action, null);
    }
  }

  public @NotNull LookupContext invokeAndGetContext(InvokerRequest invokerRequest) {
    invoke(invokerRequest);
    return myContext;
  }

  @FunctionalInterface
  interface ThrowingRunnable {
    void run() throws Exception;
  }
}
