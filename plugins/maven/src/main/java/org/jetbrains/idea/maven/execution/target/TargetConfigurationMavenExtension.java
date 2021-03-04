// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution.target;

import com.intellij.execution.target.LanguageRuntimeType;
import com.intellij.execution.target.TargetEnvironment;
import com.intellij.execution.target.TargetEnvironmentConfiguration;
import com.intellij.execution.target.TargetEnvironmentRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

public interface TargetConfigurationMavenExtension {
  @Nullable
  TargetEnvironment.UploadRoot createUploadRoot(@Nullable MavenRuntimeTargetConfiguration mavenRuntimeConfiguration,
                                                @NotNull TargetEnvironmentRequest targetEnvironmentRequest,
                                                @NotNull TargetEnvironmentConfiguration targetEnvironmentConfiguration,
                                                @NotNull LanguageRuntimeType.VolumeDescriptor volumeDescriptor,
                                                @NotNull Path localRootPath);
}
