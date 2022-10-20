/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
  @Nullable
  public abstract DomElement getScope(@NotNull DomElement element);

}
