/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

/**
 * @author peter
 */
public class ElementUndefinedEvent extends ElementChangedEvent {

  public ElementUndefinedEvent(final DomElement element) {
    super(element);
  }

  public String toString() {
    return "Undefined " + getElement();
  }

  public void accept(DomEventVisitor visitor) {
    visitor.visitElementUndefined(this);
  }
}
