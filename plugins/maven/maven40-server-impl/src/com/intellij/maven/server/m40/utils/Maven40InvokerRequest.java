// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.server.m40.utils;

import org.apache.maven.api.cli.ParserRequest;
import org.apache.maven.api.cli.extensions.CoreExtension;
import org.apache.maven.api.cli.mvn.MavenOptions;
import org.apache.maven.cling.invoker.mvn.MavenInvokerRequest;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class Maven40InvokerRequest extends MavenInvokerRequest {
  public Maven40InvokerRequest(ParserRequest parserRequest,
                               boolean parseFailed,
                               Path cwd,
                               Path installationDirectory,
                               Path userHomeDirectory,
                               Map<String, String> userProperties,
                               Map<String, String> systemProperties,
                               Path topDirectory,
                               Path rootDirectory,
                               List<CoreExtension> coreExtensions,
                               MavenOptions options) {
    super(parserRequest, parseFailed, cwd, installationDirectory, userHomeDirectory, userProperties, systemProperties, topDirectory,
          rootDirectory, coreExtensions, options);
  }

  private boolean coreExtensionsDisabled = false;

  public void disableCoreExtensions() {
    coreExtensionsDisabled = true;
  }

  @Override
  public Optional<List<CoreExtension>> coreExtensions() {
    if (coreExtensionsDisabled) return Optional.empty();
    return super.coreExtensions();
  }
}
