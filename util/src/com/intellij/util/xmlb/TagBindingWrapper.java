package com.intellij.util.xmlb;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.Text;

//todo: merge with option tag binding
class TagBindingWrapper implements Binding {
  private Binding binding;
  private String myTagName;
  private String myAttributeName;

  public TagBindingWrapper(Binding binding, final String tagName, final String attributeName) {
    this.binding = binding;

    assert binding.getBoundNodeType().isAssignableFrom(Text.class);
    myTagName = tagName;
    myAttributeName = attributeName;
  }

  public Node serialize(Object o, Node context) {
    Document ownerDocument = XmlSerializerImpl.getOwnerDocument(context);
    Element e = ownerDocument.createElement(myTagName);
    Node n = binding.serialize(o, e);
    e.setAttribute(myAttributeName, n.getNodeValue());
    return e;
  }

  public Object deserialize(Object context, Node... nodes) {
    assert nodes.length == 1;

    Element e = (Element)nodes[0];
    return binding.deserialize(context, e.getAttributeNode(myAttributeName));
  }

  public boolean isBoundTo(Node node) {
    return node instanceof Element && node.getNodeName().equals(myTagName);
  }

  public Class<? extends Node> getBoundNodeType() {
    return Element.class;
  }

  public void init() {
  }
}
