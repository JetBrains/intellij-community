package com.intellij.pom.xml.events;

import com.intellij.psi.xml.XmlText;

public interface XmlTextChanged extends XmlChange {
  String getOldText();

  XmlText getText();
}
