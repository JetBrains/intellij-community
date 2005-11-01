/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

/**
 * @author peter
 */
public class ElementUndefinedEvent implements DomEvent{
  private DomElement myElement;

  public ElementUndefinedEvent(final DomElement element) {
    myElement = element;
  }

  public DomElement getElement() {
    return myElement;
  }

  public String toString() {
    return "Undefined " + myElement;
  }

  public void accept(DomEventVisitor visitor) {
    visitor.visitElementUndefined(this);
  }
}
