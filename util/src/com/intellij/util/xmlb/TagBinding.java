package com.intellij.util.xmlb;

import com.intellij.util.DOMUtil;
import com.intellij.util.xmlb.annotations.Property;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

class TagBinding implements Binding {
  private Accessor accessor;
  private String myTagName;
  private Binding binding;

  public TagBinding(Accessor accessor, Property tagAnnotation, XmlSerializerImpl xmlSerializer) {
    this.accessor = accessor;
    myTagName = tagAnnotation.tagName();
    binding = xmlSerializer.getBinding(accessor);
  }

  public Node serialize(Object o, Node context) {
    Document ownerDocument = XmlSerializerImpl.getOwnerDocument(context);
    Object value = accessor.read(o);
    if (value == null) return context;

    Element v = ownerDocument.createElement(myTagName);

    Node node = binding.serialize(value, v);
    v.appendChild(node);

    return v;
  }

  public Object deserialize(Object o, Node... nodes) {
    assert nodes.length == 1;
    Object v = binding.deserialize(o, DOMUtil.toArray(nodes[0].getChildNodes()));
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
