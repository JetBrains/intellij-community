package com.intellij.pom.xml.events;

import com.intellij.psi.xml.XmlDocument;

public interface XmlDocumentChanged extends XmlChange {
  XmlDocument getDocument();
}
