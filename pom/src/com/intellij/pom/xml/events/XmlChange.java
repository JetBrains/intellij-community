package com.intellij.pom.xml.impl.events;

import com.intellij.pom.xml.XmlChangeVisitor;


public interface XmlChange {
  void accept(XmlChangeVisitor visitor);
}
