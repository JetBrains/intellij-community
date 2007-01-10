package com.intellij.util.xmlb;

import com.intellij.util.DOMUtil;
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

    if (myAttributeName.length() != 0) {
      e.setAttribute(myAttributeName, n.getNodeValue());
    }
    else {
      e.appendChild(ownerDocument.createTextNode(n.getNodeValue()));
    }

    return e;
  }

  public Object deserialize(Object context, Node... nodes) {
    assert nodes.length == 1;

    Element e = (Element)nodes[0];
    final Node[] childNodes;
    if (myAttributeName.length() != 0) {
      childNodes = new Node[]{e.getAttributeNode(myAttributeName)};
    }
    else {
      childNodes = DOMUtil.getChildNodes(e);
    }
  
    return binding.deserialize(context, childNodes);
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
