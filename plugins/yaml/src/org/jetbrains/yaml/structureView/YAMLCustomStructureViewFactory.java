// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.structureView;

import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLFile;

/**
 * Use this extension point if custom presentation of YAML file structure is needed.
 */
public interface YAMLCustomStructureViewFactory {
  ExtensionPointName<YAMLCustomStructureViewFactory> EP_NAME = ExtensionPointName.create("com.intellij.yaml.customStructureViewFactory");

  /**
   * YAML file structure view provider walks through the list of registered implementations of this interface
   * until a non-null builder is returned. If none of the implementations has provided a custom builder for a particular YAML file,
   * the default one will be used.
   *
   * @param yamlFile a YAML file
   * @return a structure view builder for the given YAML file or null if the file doesn't need customized structure view.
   */
  @Nullable
  StructureViewBuilder getStructureViewBuilder(final @NotNull YAMLFile yamlFile);
}
