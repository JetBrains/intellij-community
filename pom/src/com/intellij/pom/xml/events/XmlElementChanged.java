package com.intellij.pom.xml.impl.events;

import com.intellij.psi.xml.XmlElement;
import com.intellij.pom.xml.XmlChangeVisitor;

public interface XmlElementChanged extends XmlChange {
  XmlElement getElement();

  void accept(XmlChangeVisitor visitor);
}
