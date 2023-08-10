// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.html;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.xml.XmlElement;
import com.intellij.xml.impl.XmlEnumerationDescriptor;
import com.intellij.xml.util.XmlEnumeratedValueReference;

public class HtmlEnumeratedValueReference extends XmlEnumeratedValueReference {
  public HtmlEnumeratedValueReference(XmlElement value, XmlEnumerationDescriptor descriptor, TextRange range) {
    super(value, descriptor, range);
    mySoft = true;
  }
}
