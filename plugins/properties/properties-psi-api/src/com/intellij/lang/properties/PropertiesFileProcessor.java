/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.lang.properties;

import com.intellij.lang.properties.psi.PropertiesFile;

@FunctionalInterface
public interface PropertiesFileProcessor {
  boolean process(String baseName, PropertiesFile propertiesFile);
}
