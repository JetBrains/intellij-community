package com.intellij.pom.xml.events;

import com.intellij.psi.xml.XmlTag;
import com.intellij.pom.xml.XmlChangeVisitor;

public interface XmlAttributeSet extends XmlChange {
  String getName();

  String getValue();

  XmlTag getTag();

  void accept(XmlChangeVisitor visitor);
}
