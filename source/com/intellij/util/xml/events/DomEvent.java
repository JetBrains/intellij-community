/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.events;

import com.intellij.util.xml.DomEventVisitor;

/**
 * @author peter
 */
public interface DomEvent {
  void accept(DomEventVisitor visitor);
}
