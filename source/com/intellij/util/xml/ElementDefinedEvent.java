/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

/**
 * @author peter
 */
public class ElementDefinedEvent extends ElementChangedEvent{
  public ElementDefinedEvent(final DomElement element) {
    super(element);
  }

  public String toString() {
    return "Defined " + getElement();
  }

  public void accept(DomEventVisitor visitor) {
    visitor.visitElementDefined(this);
  }
}
