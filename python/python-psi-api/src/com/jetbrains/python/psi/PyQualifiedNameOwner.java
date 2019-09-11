/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.psi;

import org.jetbrains.annotations.Nullable;

/**
 * Base class for elements that have a qualified name (classes and functions).
 *
 * @author yole
 */
public interface PyQualifiedNameOwner extends PyElement {
  /**
   * Returns the qualified name of the element.
   *
   * @return the qualified name of the element, or null if the element doesn't have a name (for example, it is a lambda expression) or
   * is contained inside an element that doesn't have a qualified name.
   */
  @Nullable
  String getQualifiedName();
}
