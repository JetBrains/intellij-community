// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.html;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.xml.XmlElement;
import com.intellij.xml.impl.XmlEnumerationDescriptor;
import com.intellij.xml.util.XmlEnumeratedReferenceSet;
import com.intellij.xml.util.XmlEnumeratedValueReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class HtmlEnumeratedReferenceSet extends XmlEnumeratedReferenceSet {
  public HtmlEnumeratedReferenceSet(@NotNull XmlElement element,
                                    XmlEnumerationDescriptor descriptor) {
    super(element, descriptor);
  }

  @Override
  protected @Nullable XmlEnumeratedValueReference createReference(TextRange range, int index) {
    return new HtmlEnumeratedValueReference((XmlElement)getElement(), myDescriptor, range);
  }
}
