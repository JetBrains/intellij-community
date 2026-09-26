// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom.toolchains;

import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenDomElement;

public interface MavenDomToolchain extends MavenDomElement {
  @NotNull
  GenericDomValue<String> getType();

  @NotNull
  MavenDomProvidesToolchain getProvides();

  @NotNull
  MavenDomConfigurationToolchain getConfiguration();
}
