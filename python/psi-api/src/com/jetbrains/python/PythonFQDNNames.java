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
package com.jetbrains.python;

import com.jetbrains.python.nameResolver.FQNamesProvider;
import org.jetbrains.annotations.NotNull;

/**
 * Python (not django) names
 * @author Ilya.Kazakevich
 */
public class PythonFQDNNames implements FQNamesProvider {

  /**
   * dict()
   */
  public static final PythonFQDNNames DICT_CLASS = new PythonFQDNNames(true, "dict"); // TODO: Add other dict-like types

  private final boolean myIsClass;
  @NotNull
  private final String[] myNames;

  private PythonFQDNNames(final boolean isClass, @NotNull final String... names) {
    myIsClass = isClass;
    myNames = names;
  }

  @NotNull
  @Override
  public String[] getNames() {
    return myNames.clone();
  }

  @Override
  public boolean isClass() {
    return myIsClass;
  }
}
