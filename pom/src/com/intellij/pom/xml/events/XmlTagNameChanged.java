package com.intellij.pom.xml.impl.events;

import com.intellij.psi.xml.XmlTag;
import com.intellij.pom.xml.XmlChangeVisitor;

public interface XmlTagNameChanged extends XmlChange {
  String getOldName();

  XmlTag getTag();

  void accept(XmlChangeVisitor visitor);
}
