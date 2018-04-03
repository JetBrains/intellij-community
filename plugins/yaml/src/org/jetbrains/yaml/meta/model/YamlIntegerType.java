/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.yaml.meta.model;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Experimental
public class YamlIntegerType extends YamlScalarType {
  private static YamlIntegerType SHARED_INSTANCE = new YamlIntegerType();

  public static YamlIntegerType getInstance() {
    return SHARED_INSTANCE;
  }

  public YamlIntegerType() {
    super("yaml:integer");
  }
}
