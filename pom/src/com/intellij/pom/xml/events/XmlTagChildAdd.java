package com.intellij.pom.xml.impl.events;

import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagChild;
import com.intellij.pom.xml.XmlChangeVisitor;

public interface XmlTagChildAdd extends XmlChange {
  XmlTag getTag();

  XmlTagChild getChild();

  void accept(XmlChangeVisitor visitor);
}
