// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties.parsing;

import com.intellij.psi.stubs.IStubElementType;

public interface PropertiesElementTypes {
  IStubElementType PROPERTY = new PropertyStubElementType();
  PropertyListStubElementType PROPERTIES_LIST = new PropertyListStubElementType();
}
