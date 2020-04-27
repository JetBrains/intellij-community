// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.xml;

import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.xml.XmlNSDescriptor;
import org.jetbrains.annotations.Nullable;

public interface XmlDocument extends XmlElement, PsiMetaOwner {
  @Nullable XmlProlog getProlog();
  @Nullable XmlTag getRootTag();

  XmlNSDescriptor getRootTagNSDescriptor();
  XmlNSDescriptor getDefaultNSDescriptor(final String namespace, final boolean strict);
}
