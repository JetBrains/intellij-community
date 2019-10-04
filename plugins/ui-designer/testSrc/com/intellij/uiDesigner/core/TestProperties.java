// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.core;// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

import java.io.IOException;
import java.io.StringReader;
import java.util.PropertyResourceBundle;

// For JDK 11 `com.intellij.uiDesigner.core.TestProperties` bundle try load over class
public class TestProperties extends PropertyResourceBundle {
  public static final String TEST_PROPERTY_CONTENT = "test=Test Value\nmnemonic=Mne&monic";

  public TestProperties() throws IOException {
    super(new StringReader(TEST_PROPERTY_CONTENT));
  }
}
