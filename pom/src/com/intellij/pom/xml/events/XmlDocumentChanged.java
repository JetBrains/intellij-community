package com.intellij.pom.xml.events;

import com.intellij.pom.xml.XmlChangeVisitor;
import com.intellij.pom.xml.events.XmlChange;

public interface XmlDocumentChanged extends XmlChange {
  void accept(XmlChangeVisitor visitor);
}
