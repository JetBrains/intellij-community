package com.intellij.xml;

/**
 * @author Eugene.Kudelevsky
 */
public interface XmlNSDescriptorEx extends  XmlNSDescriptor {
  XmlElementDescriptor getElementDescriptor(String localName, String namespace);
}
