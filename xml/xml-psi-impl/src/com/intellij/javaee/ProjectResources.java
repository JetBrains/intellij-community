// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.javaee;

import com.intellij.openapi.components.State;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Map;

/**
 * @author Dmitry Avdeev
 */
@State(name = "ProjectResources")
final class ProjectResources extends ExternalResourceManagerExImpl {
  @Override
  public @NotNull Map<@NotNull String, @NotNull Map<@NotNull String, @NotNull ExternalResource>> computeStdResources$intellij_xml_psi_impl() {
    return Collections.emptyMap();
  }
}
