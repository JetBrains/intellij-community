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
package com.jetbrains.python.nameResolver;

import com.jetbrains.python.psi.PyQualifiedNameOwner;
import org.jetbrains.annotations.NotNull;

/**
 * Some enum value that represents one or more fully qualified names for some function
 *
 * @author Ilya.Kazakevich
 */
public interface FQNamesProvider {
  /**
   * @return one or more fully qualified names
   */
  @NotNull
  String[] getNames();


  /**
   * @return is name of class (true) or function (false)
   */
  boolean isClass();

  /**
   * @return if element should be checked by full name conformity by {@link PyQualifiedNameOwner#getQualifiedName()}
   * or only name and package should be checked
   */
  default boolean alwaysCheckQualifiedName() {
    return true;
  }
}
