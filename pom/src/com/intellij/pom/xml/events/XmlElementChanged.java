package com.intellij.pom.xml.events;

import com.intellij.psi.xml.XmlElement;

public interface XmlElementChanged extends XmlChange {
  XmlElement getElement();

}
