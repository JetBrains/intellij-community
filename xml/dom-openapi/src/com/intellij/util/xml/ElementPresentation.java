// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;

/**
 * @see DomElement#getPresentation()
 * @see ElementPresentationTemplate
 */
public abstract class ElementPresentation {
  public abstract @Nullable @NlsSafe String getElementName();

  public abstract @NlsSafe String getTypeName();

  public abstract @Nullable Icon getIcon();

  public @Nls @Nullable String getDocumentation() { return null;}
}
