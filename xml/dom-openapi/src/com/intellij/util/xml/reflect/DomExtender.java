// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml.reflect;

import com.intellij.util.xml.DomElement;
import org.jetbrains.annotations.NotNull;

/**
 * Register DOM extenders via {@code com.intellij.dom.extender} extension point.
 * <p>
 * Specify:
 * <ul>
 * <li>{@code domClass} - the DOM element class for which this extender will be called, must be equal to {@link T}</li>
 * <li>{@code extenderClass} - this class qualified name</li>
 * </ul>
 */
public abstract class DomExtender<T extends DomElement> {

  /**
   * @param t         DOM element where new children may be added to
   * @param registrar a place to register your own DOM children descriptions
   */
  public abstract void registerExtensions(@NotNull T t, @NotNull final DomExtensionsRegistrar registrar);

  /**
   * Makes stub building for extensions available.
   * To be compatible with general stubs contract, extension must NOT depend on anything beyond current file's content.
   */
  public boolean supportsStubs() {
    return true;
  }
}
