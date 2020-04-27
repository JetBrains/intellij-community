// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml.impl.schema;

import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.Nullable;

public abstract class TypeDescriptor {

  protected final XmlTag myTag;

  protected TypeDescriptor() {
    this(null);
  }

  protected TypeDescriptor(XmlTag tag) {
    myTag = tag;
  }

  @Nullable
  public XmlTag getDeclaration(){
    return myTag;
  }
}
