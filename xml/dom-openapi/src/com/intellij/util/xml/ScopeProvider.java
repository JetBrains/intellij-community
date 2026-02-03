// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Should be stateless, since its instances are cached.
 * @see Scope
 */
public abstract class ScopeProvider {

  /**
   * @param element element
   * @return scope to search within.
   * For uniqueness checking should return element, whose direct children names will be compared.
   * Basically it's parameter element's parent: {@link ParentScopeProvider}.
   * Searches within all children of returned element.
   */
  public abstract @Nullable DomElement getScope(@NotNull DomElement element);

}
