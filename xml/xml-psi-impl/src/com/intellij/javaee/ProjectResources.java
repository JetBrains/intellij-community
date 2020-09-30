// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.javaee;

import com.intellij.openapi.components.State;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Map;

/**
 * @author Dmitry Avdeev
 */
@State(name = "ProjectResources")
public final class ProjectResources extends ExternalResourceManagerExImpl {
  @Override
  protected @NotNull Map<String, Map<String, Resource>> computeStdResources() {
    return Collections.emptyMap();
  }
}
