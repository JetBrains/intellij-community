package com.intellij.pom.xml.impl.events;

import com.intellij.pom.xml.XmlChangeVisitor;
import com.intellij.psi.xml.XmlText;

public interface XmlTextChanged extends XmlChange {
  String getOldText();

  XmlText getText();

  void accept(XmlChangeVisitor visitor);
}
