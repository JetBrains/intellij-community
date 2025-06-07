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
    return switch (version) {
      case BASE -> ModelProblem.Version.BASE;
      case V20 -> ModelProblem.Version.V20;
      case V30 -> ModelProblem.Version.V30;
      case V31 -> ModelProblem.Version.V31;
      case V40 -> ModelProblem.Version.V40;
      default -> throw new IllegalArgumentException(version.toString());
    };
  }

  private static ModelProblem.Severity convertFromApiSeverity(BuilderProblem.Severity severity) {
    return switch (severity) {
      case FATAL -> ModelProblem.Severity.FATAL;
      case ERROR -> ModelProblem.Severity.ERROR;
      case WARNING -> ModelProblem.Severity.WARNING;
    };
  }
}
