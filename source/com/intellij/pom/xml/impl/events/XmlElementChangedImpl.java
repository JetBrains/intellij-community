package com.intellij.pom.xml.impl.events;

import com.intellij.psi.xml.XmlElement;
import com.intellij.pom.xml.XmlChangeVisitor;
import com.intellij.pom.xml.impl.events.XmlChange;
import com.intellij.pom.xml.impl.events.XmlElementChanged;

public class XmlElementChangedImpl implements XmlElementChanged {
  private final XmlElement myElement;

  public XmlElementChangedImpl(XmlElement treeElement) {
    myElement = treeElement;
  }

  public XmlElement getElement() {
    return myElement;
  }

  public void accept(XmlChangeVisitor visitor) {
    visitor.visitXmlElementChanged(this);
  }
}
