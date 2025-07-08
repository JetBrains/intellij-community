// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.server.m40.compat;

import com.intellij.util.text.VersionComparatorUtil;
import org.apache.maven.api.cli.InvokerRequest;
import org.apache.maven.api.cli.Options;
import org.apache.maven.api.cli.mvn.MavenOptions;
import org.apache.maven.cling.invoker.mvn.MavenContext;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;

import static org.jetbrains.idea.maven.server.MavenServerEmbedder.MAVEN_EMBEDDER_VERSION;

public class MavenContextFactory {
  public static MavenContext createMavenContext(InvokerRequest invokerRequest) {
    String mavenVersion = System.getProperty(MAVEN_EMBEDDER_VERSION);
    if (VersionComparatorUtil.compare(mavenVersion, "4.0.0-rc-3") == 0) {
      return new MavenContext(invokerRequest, false);
    }
    else {
      Constructor<?>[] constructors = MavenContext.class.getConstructors();
      if (constructors.length != 1) throw new UnsupportedOperationException("MavenContext incompatibility with current IDEA version");
      try {
        MavenOptions options = getOptions(invokerRequest);
        return (MavenContext)constructors[0].newInstance(invokerRequest, false, options);
      }
      catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
        throw new UnsupportedOperationException("MavenContext incompatibility with current IDEA version", e);
      }
    }
  }

  private static @Nullable MavenOptions getOptions(InvokerRequest invokerRequest) {
    try {
      Method method = InvokerRequest.class.getMethod("options");
      Optional<Options> options = (Optional<Options>)method.invoke(invokerRequest);
      return (MavenOptions)options.orElse(null);
    }
    catch (Exception e) {
      throw new UnsupportedOperationException("MavenContext incompatibility with current IDEA version", e);
    }
  }
}
