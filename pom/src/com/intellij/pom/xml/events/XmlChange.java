package com.intellij.pom.xml.events;

import com.intellij.pom.xml.XmlChangeVisitor;


public interface XmlChange {
  void accept(XmlChangeVisitor visitor);
}
