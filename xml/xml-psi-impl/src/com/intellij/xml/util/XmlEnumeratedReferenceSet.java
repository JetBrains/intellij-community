// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml.util;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.util.ReferenceSetBase;
import com.intellij.psi.xml.XmlElement;
import com.intellij.xml.impl.XmlEnumerationDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class XmlEnumeratedReferenceSet extends ReferenceSetBase<XmlEnumeratedValueReference> {

  protected final XmlEnumerationDescriptor myDescriptor;

  public XmlEnumeratedReferenceSet(@NotNull XmlElement element, XmlEnumerationDescriptor descriptor) {
    super(ElementManipulators.getValueText(element),element, ElementManipulators.getOffsetInElement(element), ' ');
    myDescriptor = descriptor;
  }

  @Nullable
  @Override
  protected XmlEnumeratedValueReference createReference(TextRange range, int index) {
    return new XmlEnumeratedValueReference((XmlElement)getElement(), myDescriptor, range);
  }

  @Override
  public List<XmlEnumeratedValueReference> getReferences() {
    if (myDescriptor.isList()) return super.getReferences();
    return Collections.singletonList(createReference(null, 0));
  }
}
