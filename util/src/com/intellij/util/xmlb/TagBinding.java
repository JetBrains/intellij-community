package com.intellij.util.xmlb;

import com.intellij.util.DOMUtil;
import com.intellij.util.xmlb.annotations.Tag;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class TagBinding implements Binding {
  private Accessor accessor;
  private Tag myTagAnnotation;
  private String myTagName;
  private Binding binding;

  public TagBinding(Accessor accessor, Tag tagAnnotation, XmlSerializerImpl xmlSerializer) {
    this.accessor = accessor;
    myTagAnnotation = tagAnnotation;
    myTagName = tagAnnotation.value();
    binding = xmlSerializer.getBinding(accessor);
  }

  public Node serialize(Object o, Node context) {
    Document ownerDocument = XmlSerializerImpl.getOwnerDocument(context);
    Object value = accessor.read(o);
    if (value == null) return context;

    Element v = ownerDocument.createElement(myTagName);

    Node node = binding.serialize(value, v);
    if (node != v) {
      v.appendChild(node);
    }

    return v;
  }

  public Object deserialize(Object o, Node... nodes) {
    assert nodes.length > 0;
    final Document document = nodes[0].getOwnerDocument();
    Node[] children;
    if (nodes.length == 1) {
      children = DOMUtil.toArray(nodes[0].getChildNodes());
    }
    else {
      String name = nodes[0].getNodeName();
      List<Node> childrenList = new ArrayList<Node>();
      for (Node node : nodes) {
        assert node.getNodeName().equals(name);
        childrenList.addAll(Arrays.asList(DOMUtil.toArray(node.getChildNodes())));
      }
      children = childrenList.toArray(new Node[childrenList.size()]);
    }

    if (children.length == 0) {
      children = new Node[] {document.createTextNode(myTagAnnotation.textIfEmpty())};
    }

    Object v = binding.deserialize(accessor.read(o), children);
    Object value = XmlSerializerImpl.convert(v, accessor.getValueClass());
    accessor.write(o, value);
    return o;
  }

  public boolean isBoundTo(Node node) {
    return node instanceof Element && node.getNodeName().equals(myTagName);
  }

  public Class<? extends Node> getBoundNodeType() {
    throw new UnsupportedOperationException("Method getBoundNodeType is not supported in " + getClass());
  }

  public void init() {
  }
}
