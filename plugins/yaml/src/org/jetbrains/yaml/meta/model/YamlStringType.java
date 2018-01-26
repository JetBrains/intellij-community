/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.yaml.meta.model;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Experimental
public class YamlStringType extends YamlScalarType {
  private static YamlStringType SHARED_INSTANCE = new YamlStringType();

  public static YamlStringType getInstance() {
    return SHARED_INSTANCE;
  }

  public YamlStringType() {
    super("yaml:string");
  }
}
