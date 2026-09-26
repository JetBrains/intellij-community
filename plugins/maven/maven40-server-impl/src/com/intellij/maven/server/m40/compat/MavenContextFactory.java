// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.server.m40.compat;

import com.intellij.util.text.VersionComparatorUtil;
import org.apache.maven.api.cli.InvokerRequest;
import org.apache.maven.api.cli.mvn.MavenOptions;
import org.apache.maven.cling.invoker.mvn.MavenContext;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;

import static org.jetbrains.idea.maven.server.MavenServerEmbedder.MAVEN_EMBEDDER_VERSION;

public class MavenContextFactory {
  public static MavenContext createMavenContext(InvokerRequest invokerRequest) {
    String mavenVersion = System.getProperty(MAVEN_EMBEDDER_VERSION);
    if (VersionComparatorUtil.compare(mavenVersion, "4.0.0-rc-3") == 0) {
      Constructor<?>[] constructors = MavenContext.class.getConstructors();
      @SuppressWarnings("SSBasedInspection")
      var constructor = Arrays.stream(constructors).filter(it -> it.getParameterCount() == 2).findFirst().orElse(null);
      if (constructors.length != 2 || constructor == null) throw new UnsupportedOperationException("MavenContext: Wrong constructors. This maven is incompatibile with current IDEA version");
      try {
        return (MavenContext)constructor.newInstance(invokerRequest, false);
      }
      catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
        throw new UnsupportedOperationException(" This maven is incompatibile with current IDEA version", e);
      }
    }
    else {
      var mavenOptions = invokerRequest.options().orElse(null);
      return new MavenContext(invokerRequest, false, (MavenOptions)mavenOptions);
    }
  }
}
