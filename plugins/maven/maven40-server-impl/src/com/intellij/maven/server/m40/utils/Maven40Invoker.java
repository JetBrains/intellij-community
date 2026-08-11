// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.server.m40.utils;

import com.intellij.maven.server.m40.InvokerWithoutCoreExtensions;
import com.intellij.maven.server.m40.compat.CompatResidentMavenInvoker;
import org.apache.maven.api.cli.InvokerRequest;
import org.apache.maven.api.cli.InvokerException;
import org.apache.maven.cling.invoker.LookupContext;
import org.apache.maven.cling.invoker.ProtoLookup;
import org.apache.maven.cling.invoker.mvn.MavenContext;
import org.apache.maven.cling.invoker.mvn.MavenInvoker;
import org.apache.maven.execution.MavenExecutionRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.server.MavenServerGlobals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Maven40Invoker extends CompatResidentMavenInvoker {
  MavenContext myContext = null;

  /**
   * Closeables registered by {@link #createTerminal} (terminal {@code systemUninstall}, build-log appender,
   * stream flushers). We detach them from the per-invocation context so that the {@code context.close()} run by
   * {@link org.apache.maven.cling.invoker.LookupInvoker#invoke} at the end of the single bootstrap {@code doInvoke}
   * does not close the terminal we keep reusing. They are closed in {@link #close()} when the embedder is released.
   */
  private List<AutoCloseable> myResidentCloseables = List.of();

  public Maven40Invoker(ProtoLookup protoLookup) {
    super(protoLookup);
  }

  @Override
  protected int doInvoke(MavenContext context) throws Exception {
    validate(context);
    pushCoreProperties(context);
    pushUserProperties(context);
    configureLogging(context);
    int closeablesBefore = context.closeables.size();
    createTerminal(context);
    // LookupInvoker.invoke() wraps doInvoke() in a try-with-resources and runs context.close() the moment this
    // method returns. createTerminal() registered MessageUtils::systemUninstall (and the build-log/stream closeables)
    // on the context; closing them would close the resident terminal that we reuse in createMavenExecutionRequest().
    // Detach them here and tear them down only on release() (see close()).
    List<AutoCloseable> terminalCloseables = context.closeables.subList(closeablesBefore, context.closeables.size());
    myResidentCloseables = new ArrayList<>(terminalCloseables);
    terminalCloseables.clear();
    activateLogging(context);
    helpOrVersionAndMayExit(context);
    preCommands(context);
    //noinspection CastToIncompatibleInterface
    tryRunAndRetryOnFailure(
      "container",
      () -> container(context),
      () -> ((InvokerWithoutCoreExtensions)context.invokerRequest).disableCoreExtensions()
    );
    postContainer(context);
    pushUserProperties(context); // after PropertyContributor SPI
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

  @Override
  public void close() throws InvokerException {
    // Close the terminal-bound closeables detached in doInvoke(), reverse order to mirror LookupContext.close().
    List<AutoCloseable> cs = new ArrayList<>(myResidentCloseables);
    Collections.reverse(cs);
    for (AutoCloseable c : cs) {
      if (c != null) {
        try {
          c.close();
        }
        catch (Exception e) {
          MavenServerGlobals.getLogger().warn("Maven40Invoker.close (terminal): " + e.getMessage(), e);
        }
      }
    }
    myResidentCloseables = List.of();
    super.close();
  }

  /**
   * adapted from {@link MavenInvoker#execute(MavenContext)}
   */
  public MavenExecutionRequest createMavenExecutionRequest() throws Exception {
    MavenContext context = myContext;
    MavenExecutionRequest request = prepareMavenExecutionRequest();
    toolchains(context, request);
    populateRequest(context, context.lookup, request);
    //return doExecute(context, request);
    return request;
  }

  private static boolean tryRun(String methodName, ThrowingRunnable action, Runnable onFailure) {
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

  private static void tryRunAndRetryOnFailure(String methodName, ThrowingRunnable action, Runnable onFailure) {
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
