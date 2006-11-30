package com.intellij.util.xmlb;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

abstract class AbstractCollectionBinding implements Binding {
  private Binding myElementBinding;
  private Class myElementType;
  private String myTagName;

  public AbstractCollectionBinding(Class elementType, XmlSerializerImpl xmlSerializer, String tagName) {
    myElementType = elementType;
    myTagName = tagName;
    myElementBinding = xmlSerializer.getBinding(elementType);

    if (!myElementBinding.getBoundNodeType().isAssignableFrom(Element.class)) {
      myElementBinding = new OptionTagBindingWrapper(myElementBinding);
    }
  }

  abstract Object processResult(List result, Object target);
  abstract Collection getCollection(Object o);

  public Node serialize(Object o, Node context) {
    Collection collection = getCollection(o);
    Document ownerDocument = XmlSerializerImpl.getOwnerDocument(context);

    Element c = ownerDocument.createElement(myTagName);
    for (Object e : collection) {
      c.appendChild(myElementBinding.serialize(e, c));
    }

    return c;
  }

  public Object deserialize(Object o, Node node) {
    List result = new ArrayList();

    Element e = (Element)node;
    NodeList childNodes = e.getChildNodes();
    for (int i = 0; i < childNodes.getLength(); i++) {
      Object v = myElementBinding.deserialize(o, childNodes.item(i));
      //noinspection unchecked
      result.add(v);
    }

    return processResult(result, o);
  }

  public boolean isBoundTo(Node node) {
    return node instanceof Element && node.getNodeName().equals(Constants.COLLECTION);
  }

  public Class<? extends Node> getBoundNodeType() {
    throw new UnsupportedOperationException("Method getBoundNodeType is not supported in " + getClass());
  }


  public Class getElementType() {
    return myElementType;
  }
}
