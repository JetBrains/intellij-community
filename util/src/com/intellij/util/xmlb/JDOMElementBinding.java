package com.intellij.util.xmlb;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

class JDOMElementBinding implements Binding {
  private Accessor myAccessor;
  private String myTagName;

  public JDOMElementBinding(final XmlSerializerImpl xmlSerializer, final Accessor accessor) {
    myAccessor = accessor;
    final Tag tag = XmlSerializerImpl.findAnnotation(myAccessor.getAnnotations(), Tag.class);
    assert tag != null : "jdom.Element property without @Tag annotation: " + accessor;
    myTagName = tag.value();
  }

  public Node serialize(Object o, Node context) {
    throw new UnsupportedOperationException("Method serialize is not supported in " + getClass());
  }

  @Nullable
  public Object deserialize(Object context, Node... nodes) {
    org.jdom.Element[] result = new org.jdom.Element[nodes.length];

    for (int i = 0; i < nodes.length; i++) {
      Node n = nodes[i];
      result[i] = JDOMUtil.convertFromDOM((Element)n); 
    }

    if (myAccessor.getValueClass().isArray()) {
      myAccessor.write(context, result);
    }
    else {
      assert result.length == 1;
      myAccessor.write(context, result[0]);
    }
    return context;
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
