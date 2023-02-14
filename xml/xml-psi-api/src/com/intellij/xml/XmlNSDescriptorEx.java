package com.intellij.xml;

public interface XmlNSDescriptorEx extends  XmlNSDescriptor {
  XmlElementDescriptor getElementDescriptor(String localName, String namespace);
}
