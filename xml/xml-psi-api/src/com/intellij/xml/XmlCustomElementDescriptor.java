// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml;

import com.intellij.psi.xml.XmlTag;

/**
 * Provides additional API to determine, whether a particular XML or HTML element
 * is custom. A "custom" element would have a different color highlighting
 * according to settings for "Custom Tag Name". Additionally, if it is an HTML element
 * it can be recognized as a custom tag, even having a regular HTML name; for instance in
 * web frameworks templates.
 */
public interface XmlCustomElementDescriptor extends XmlElementDescriptor {

  /**
   * @return true, if the element should be highlighted as "Custom Tag Name", or it is an HTML tag
   * with standard name, but custom behavior (e.g. in web framework templates).
   * For HTML files, there is also independent logic that checks that if no html tags with such name,
   * then IDE will use "Custom tag name" highlighting
   */
  boolean isCustomElement();

  /**
   * @see XmlCustomElementDescriptor#isCustomElement()
   */
  static boolean isCustomElement(XmlTag tag) {
    return tag.getDescriptor() instanceof XmlCustomElementDescriptor descriptor
           && descriptor.isCustomElement();
  }
}