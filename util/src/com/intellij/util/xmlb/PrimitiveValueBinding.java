package com.intellij.util.xmlb;

import com.intellij.util.DOMUtil;
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
  public Object deserialize(Object o, Node... nodes) {
    assert nodes != null;

    if (nodes.length == 0) {
      return XmlSerializerImpl.convert("", myType);
    }

    String value;
    if (nodes.length > 1) {
      value = DOMUtil.concatTextNodesValues(nodes);
    }
    else {
      assert nodes[0] != null;
      value = nodes[0].getNodeValue();
    }

    return XmlSerializerImpl.convert(value, myType);
  }

  public boolean isBoundTo(Node node) {
    throw new UnsupportedOperationException("Method isBoundTo is not supported in " + getClass());
  }

  public Class<? extends Node> getBoundNodeType() {
    return Text.class;
  }

  public void init() {
  }
}
