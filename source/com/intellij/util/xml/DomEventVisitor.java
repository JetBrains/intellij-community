/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

/**
 * @author peter
 */
public interface DomEventVisitor {
  void visitValueChangeEvent(final ValueChangeEvent event);

  void visitElementDefined(final ElementDefinedEvent event);

  void visitElementUndefined(final ElementUndefinedEvent event);

  void visitElementChangedEvent(final ElementChangedEvent event);

  void visitAttributeValueChangeEvent(final AttributeValueChangeEvent event);
}
