// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.javaee;

import com.intellij.openapi.extensions.ExtensionPointName;

/**
 * Allows adding/ignoring standard resources.
 * <p>
 * Consider using {@link StandardResourceEP} when only adding is required.
 * </p>
 *
 * @author Dmitry Avdeev
 * @see ExternalResourceManager
 */
public interface StandardResourceProvider {
  ExtensionPointName<StandardResourceProvider> EP_NAME = new ExtensionPointName<>("com.intellij.standardResourceProvider");

  void registerResources(ResourceRegistrar registrar);
}
