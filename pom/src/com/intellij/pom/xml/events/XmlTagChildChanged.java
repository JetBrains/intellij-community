package com.intellij.pom.xml.events;

import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagChild;

public interface XmlTagChildChanged extends XmlChange {
  XmlTag getTag();

  XmlTagChild getChild();
}
