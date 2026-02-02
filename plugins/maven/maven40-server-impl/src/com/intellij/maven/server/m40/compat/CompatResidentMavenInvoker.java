// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.server.m40.compat;

import org.apache.maven.api.cli.InvokerException;
import org.apache.maven.api.cli.InvokerRequest;
import org.apache.maven.api.services.Lookup;
import org.apache.maven.cling.invoker.mvn.MavenContext;
import org.apache.maven.cling.invoker.mvn.MavenInvoker;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resident invoker implementation, specialization of Maven Invoker, but keeps Maven instance resident. This implies, that
 * things like environment, system properties, extensions etc. are loaded only once. It is caller duty to ensure
 * that subsequent call is right for the resident instance (ie no env change or different extension needed).
 * This implementation "pre-populates" MavenContext with pre-existing stuff (except for very first call)
 * and does not let DI container to be closed.
 */
public class CompatResidentMavenInvoker extends MavenInvoker {

  private final ConcurrentHashMap<String, MavenContext> residentContext;

  public CompatResidentMavenInvoker(Lookup protoLookup) {
    super(protoLookup, null);
    this.residentContext = new ConcurrentHashMap<>();
  }

  @Override
  public void close() throws InvokerException {
    ArrayList<Exception> exceptions = new ArrayList<>();
    for (MavenContext context : residentContext.values()) {
      try {
        context.doCloseContainer();
      } catch (Exception e) {
        exceptions.add(e);
      }
    }
    if (!exceptions.isEmpty()) {
      InvokerException exception = new InvokerException("Could not cleanly shut down context pool");
      exceptions.forEach(exception::addSuppressed);
      throw exception;
    }
  }

  @Override
  protected MavenContext createContext(InvokerRequest invokerRequest) {
    // TODO: in a moment Maven stop pushing user properties to system properties (and maybe something more)
    // and allow multiple instances per JVM, this may become a pool? derive key based in invokerRequest?
    MavenContext result = residentContext.computeIfAbsent(
      "resident",
      k -> MavenContextFactory.createMavenContext(invokerRequest));
    return copyIfDifferent(result, invokerRequest);
  }

  protected MavenContext copyIfDifferent(MavenContext mavenContext, InvokerRequest invokerRequest) {
    if (invokerRequest == mavenContext.invokerRequest) {
      return mavenContext;
    }
    MavenContext shadow = MavenContextFactory.createMavenContext(invokerRequest);

    // we carry over only "resident" things
    shadow.containerCapsule = mavenContext.containerCapsule;
    shadow.lookup = mavenContext.lookup;
    shadow.eventSpyDispatcher = mavenContext.eventSpyDispatcher;
    shadow.maven = mavenContext.maven;

    return shadow;
  }
}
