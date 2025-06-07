// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml.events;

import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.annotations.NotNull;

public class DomEvent {
  public static final DomEvent[] EMPTY_ARRAY = new DomEvent[0];

  private final DomElement myElement;
  private final boolean myDefined;

  public DomEvent(final @NotNull DomElement element, boolean defined) {
    myDefined = defined;
    myElement = DomUtil.getFileElement(element);
  }

  public final DomElement getElement() {
    return myElement;
  }

  public boolean isDefined() {
    return myDefined;
  }

  @Override
  public String toString() {
    return (myDefined ? "Defined " : "Changed ") + myElement;
  }

}
