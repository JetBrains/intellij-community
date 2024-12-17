// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.server.m40.utils;

import org.apache.maven.api.cli.ParserRequest;
import org.apache.maven.api.cli.extensions.CoreExtension;
import org.apache.maven.api.cli.mvn.MavenOptions;
import org.apache.maven.cling.invoker.mvn.MavenInvokerRequest;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class Maven40InvokerRequest extends MavenInvokerRequest {
  public Maven40InvokerRequest(ParserRequest parserRequest,
                               Path cwd,
                               Path installationDirectory,
                               Path userHomeDirectory,
                               Map<String, String> userProperties,
                               Map<String, String> systemProperties,
                               Path topDirectory,
                               Path rootDirectory,
                               InputStream in,
                               OutputStream out,
                               OutputStream err,
                               List<CoreExtension> coreExtensions,
                               List<String> jvmArguments,
                               MavenOptions options) {
    super(parserRequest, cwd, installationDirectory, userHomeDirectory, userProperties, systemProperties, topDirectory, rootDirectory, in,
          out, err, coreExtensions, jvmArguments, options);
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
