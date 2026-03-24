// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom.toolchains;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenDomElement;

import java.util.List;

public interface MavenDomToolchainsModel extends MavenDomElement {
  @NotNull
  List<MavenDomToolchain> getToolchains();

  MavenDomToolchain addToolchain();
}
