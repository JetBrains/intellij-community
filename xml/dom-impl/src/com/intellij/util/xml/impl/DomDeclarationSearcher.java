// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml.impl;

import com.intellij.util.xml.AbstractDomDeclarationSearcher;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomTarget;
import com.intellij.util.xml.NameValue;
import org.jetbrains.annotations.Nullable;

public class DomDeclarationSearcher extends AbstractDomDeclarationSearcher {

  @Override
  protected @Nullable DomTarget createDomTarget(DomElement parent, DomElement nameElement) {
    final NameValue nameValue = nameElement.getAnnotation(NameValue.class);
    if (nameValue != null && nameValue.referencable()) {
      return DomTarget.getTarget(parent);
    }
    return null;
  }

}
