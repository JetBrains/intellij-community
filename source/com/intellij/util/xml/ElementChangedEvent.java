/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

/**
 * @author peter
 */
public class ElementChangedEvent implements DomEvent {
  private final DomElement myElement;

  protected ElementChangedEvent(final DomElement element) {
    assert element != null;
    myElement = element;
  }

  public final DomElement getElement() {
    return myElement;
  }

  public void accept(DomEventVisitor visitor) {
    visitor.visitElementChangedEvent(this);
  }
}
