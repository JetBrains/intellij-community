package com.intellij.pom.xml.events;

import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagChild;
import com.intellij.pom.xml.XmlChangeVisitor;
import com.intellij.pom.xml.events.XmlChange;

public interface XmlTagChildRemoved extends XmlChange {
  XmlTag getTag();

  XmlTagChild getChild();
}
