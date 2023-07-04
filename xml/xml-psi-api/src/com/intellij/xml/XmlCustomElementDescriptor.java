// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml;

import com.intellij.psi.xml.XmlTag;

/**
 * Interface-marker for customization of behaviour "Custom Tag Name" highlighting
 */
public interface XmlCustomElementDescriptor {

  /**
   * @return true, if the element should be highlighted as "Custom Tag Name".
   * For HTML files, there is also independent logic that checks that if no html tags with such name,
   * then IDE will use "Custom tag name" highlighting
   */
  boolean isCustomElement();

  static boolean isCustomElement(XmlTag tag) {
    return tag.getDescriptor() instanceof XmlCustomElementDescriptor descriptor
           && descriptor.isCustomElement();
  }
}