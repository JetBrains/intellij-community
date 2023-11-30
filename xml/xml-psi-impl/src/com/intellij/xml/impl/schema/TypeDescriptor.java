// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  public @Nullable XmlTag getDeclaration(){
    return myTag;
  }
}
