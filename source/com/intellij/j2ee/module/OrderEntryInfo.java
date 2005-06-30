package com.intellij.j2ee.module;

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.JDOMExternalizer;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.containers.HashMap;
import org.jdom.Element;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author cdr
 * @deprecated
 */
class OrderEntryInfo implements JDOMExternalizable {
  public boolean copy;
  public String URI="";
  private Map<String,String> attributes = new HashMap<String, String>();

  public void writeExternal(Element element) throws WriteExternalException {
    JDOMExternalizer.write(element,"copy",copy);
    JDOMExternalizer.write(element,"URI",URI);
    writeAttributes(element);
  }

  private void writeAttributes(Element element) {
    if (attributes.size() == 0) return;
    Element root = new Element("attributes");
    element.addContent(root);
    Set<String> names = attributes.keySet();
    for (String name : names) {
      String value = attributes.get(name);
      Element attr = new Element("attribute");
      attr.setAttribute("name", name);
      attr.setAttribute("value", value);
      root.addContent(attr);
    }
  }
  private void readAttributes(Element element) {
    Element attrs = element.getChild("attributes");
    if (attrs == null) return;
    List roots = attrs.getChildren("attribute");
    if (roots.size() == 0) return;
    for (Object root : roots) {
      Element attr = (Element)root;
      String name = attr.getAttributeValue("name");
      String value = attr.getAttributeValue("value");
      attributes.put(name, value);
    }
  }

  public void readExternal(Element element) throws InvalidDataException {
    copy = JDOMExternalizer.readBoolean(element,"copy");
    URI = JDOMExternalizer.readString(element,"URI");
    readAttributes(element);
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof OrderEntryInfo)) return false;

    final OrderEntryInfo orderEntryInfo = (OrderEntryInfo)o;

    if (copy != orderEntryInfo.copy) return false;
    if (URI != null ? !URI.equals(orderEntryInfo.URI) : orderEntryInfo.URI != null) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = copy ? 1 : 0;
    result = 29 * result + (URI != null ? URI.hashCode() : 0);
    return result;
  }

  public Map<String, String> getAttributes() {
    return attributes;
  }
}
