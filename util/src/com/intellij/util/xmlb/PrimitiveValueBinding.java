package com.intellij.util.xmlb;

import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.Text;

class PrimitiveValueBinding implements Binding {
  private final Class<?> myType;


  public PrimitiveValueBinding(Class<?> myType) {
    this.myType = myType;
  }

  public Node serialize(Object o, Node context) {
    Document ownerDocument = XmlSerializerImpl.getOwnerDocument(context);
    return ownerDocument.createTextNode(String.valueOf(o));
  }

  @Nullable
  public Object deserialize(Object o, Node node) {
    return XmlSerializerImpl.convert(node.getNodeValue(), myType);
  }

  public boolean isBoundTo(Node node) {
    throw new UnsupportedOperationException("Method isBoundTo is not supported in " + getClass());
  }

  public Class<? extends Node> getBoundNodeType() {
    return Text.class;
  }
}
