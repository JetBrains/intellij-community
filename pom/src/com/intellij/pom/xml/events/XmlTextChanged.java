package com.intellij.pom.xml.events;

import com.intellij.pom.xml.XmlChangeVisitor;
import com.intellij.pom.xml.events.XmlChange;
import com.intellij.psi.xml.XmlText;

public interface XmlTextChanged extends XmlChange {
  String getOldText();

  XmlText getText();

  void accept(XmlChangeVisitor visitor);
}
