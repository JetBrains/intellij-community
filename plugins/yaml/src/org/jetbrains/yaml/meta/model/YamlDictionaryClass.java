/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.yaml.meta.model;

import org.jetbrains.annotations.ApiStatus;

/**
 * Represents type with single level of arbitrary named scalar values.
 * For arbitrary deep structure use {@link YamlUnstructuredClass}
 */
@ApiStatus.Experimental
public class YamlDictionaryClass extends YamlMetaClass {
  public YamlDictionaryClass(String typeName, boolean allowEmptyValues) {
    super(typeName);
    addStringFeature("map:<any-key>").withAnyName().withEmptyValueAllowed(allowEmptyValues);
  }
}
