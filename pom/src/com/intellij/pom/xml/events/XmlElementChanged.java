package com.intellij.pom.xml.events;

import com.intellij.psi.xml.XmlElement;
import com.intellij.pom.xml.XmlChangeVisitor;
import com.intellij.pom.xml.events.XmlChange;

public interface XmlElementChanged extends XmlChange {
  XmlElement getElement();

  void accept(XmlChangeVisitor visitor);
}
