// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties.psi;

import java.util.Properties;

/**
 * Describes how to interpret property keys and values when they loaded or stored
 */
public enum PropertyKeyValueFormat {
  /** As stored in *.properties file: leading spaces and line breaks are escaped by '\', etc */
  FILE,
  /** As shown in resources bundle editor: line breaks are stored as is, other special characters are escaped */
  PRESENTABLE,
  /** As loaded to memory by {@link Properties}: line breaks and other special characters are stored as is */
  MEMORY
}
