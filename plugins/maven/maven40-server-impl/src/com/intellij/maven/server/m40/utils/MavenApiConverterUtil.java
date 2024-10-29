// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.server.m40.utils;

import org.apache.maven.api.services.BuilderProblem;
import org.apache.maven.model.building.DefaultModelProblem;
import org.apache.maven.model.building.ModelProblem;

import java.util.List;
import java.util.stream.Collectors;

public final class MavenApiConverterUtil {
  private MavenApiConverterUtil() { }

  @SuppressWarnings("SSBasedInspection")
  public static List<ModelProblem> convertFromApiProblems(List<org.apache.maven.api.services.ModelProblem> problems) {
    return problems.stream().map(it -> new DefaultModelProblem(
      it.getMessage(),
      convertFromApiSeverity(it.getSeverity()),
      convertFromApiVersion(it.getVersion()),
      it.getSource(),
      it.getLineNumber(),
      it.getColumnNumber(),
      it.getModelId(),
      it.getException()
    )).collect(Collectors.toList());
  }

  private static ModelProblem.Version convertFromApiVersion(org.apache.maven.api.services.ModelProblem.Version version) {
    switch (version) {
      case BASE:
        return ModelProblem.Version.BASE;
      case V20:
        return ModelProblem.Version.V20;
      case V30:
        return ModelProblem.Version.V30;
      case V31:
        return ModelProblem.Version.V31;
      case V40:
        return ModelProblem.Version.V40;
    }
    throw new IllegalArgumentException(version.toString());
  }

  static private ModelProblem.Severity convertFromApiSeverity(BuilderProblem.Severity severity) {
    switch (severity) {
      case FATAL:
        return ModelProblem.Severity.FATAL;
      case ERROR:
        return ModelProblem.Severity.ERROR;
      case WARNING:
        return ModelProblem.Severity.WARNING;
    }
    throw new IllegalArgumentException(severity.toString());
  }
}
