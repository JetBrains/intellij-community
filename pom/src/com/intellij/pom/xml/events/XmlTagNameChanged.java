package com.intellij.pom.xml.events;

import com.intellij.psi.xml.XmlTag;
import com.intellij.pom.xml.XmlChangeVisitor;
import com.intellij.pom.xml.events.XmlChange;

public interface XmlTagNameChanged extends XmlChange {
  String getOldName();

  XmlTag getTag();

  void accept(XmlChangeVisitor visitor);
}
