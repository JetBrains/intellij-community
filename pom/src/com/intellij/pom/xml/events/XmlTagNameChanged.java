package com.intellij.pom.xml.events;

import com.intellij.psi.xml.XmlTag;

public interface XmlTagNameChanged extends XmlChange {
  String getOldName();

  XmlTag getTag();
}
