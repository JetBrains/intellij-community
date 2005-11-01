/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

/**
 * @author peter
 */
public interface DomEventVisitor {
  void visitAttributeChangeEvent(final AttributeChangeEvent event);

  void visitValueChangeEvent(final ValueChangeEvent event);

  void visitElementDefined(final ElementDefinedEvent event);

  void visitElementUndefined(final ElementUndefinedEvent event);
}
