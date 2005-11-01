/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

/**
 * @author peter
 */
public class ElementDefinedEvent implements DomEvent{
  private DomElement myElement;

  public ElementDefinedEvent(final DomElement element) {
    myElement = element;
  }

  public DomElement getElement() {
    return myElement;
  }

  public void accept(DomEventVisitor visitor) {
    visitor.visitElementDefined(this);
  }
}
