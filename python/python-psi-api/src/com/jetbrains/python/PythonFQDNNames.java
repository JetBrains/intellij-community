// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.jetbrains.python.nameResolver.FQNamesProvider;
import org.jetbrains.annotations.NotNull;

/**
 * Python (not django) names
 * @author Ilya.Kazakevich
 */
public final class PythonFQDNNames implements FQNamesProvider {

  /**
   * dict()
   */
  public static final PythonFQDNNames DICT_CLASS = new PythonFQDNNames(true, "dict"); // TODO: Add other dict-like types

  private final boolean myIsClass;
  private final String @NotNull [] myNames;

  private PythonFQDNNames(final boolean isClass, final String @NotNull ... names) {
    myIsClass = isClass;
    myNames = names;
  }

  @Override
  public String @NotNull [] getNames() {
    return myNames.clone();
  }

  @Override
  public boolean isClass() {
    return myIsClass;
  }
}
