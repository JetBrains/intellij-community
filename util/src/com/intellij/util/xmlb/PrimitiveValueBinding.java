package com.intellij.util.xmlb;

import com.intellij.openapi.util.JDOMUtil;
import org.jdom.Text;
import org.jetbrains.annotations.Nullable;

class PrimitiveValueBinding implements Binding {
  private final Class<?> myType;


  public PrimitiveValueBinding(Class<?> myType) {
    this.myType = myType;
  }

  public Object serialize(Object o, Object context) {
    return new Text(String.valueOf(o));
  }

  @Nullable
  public Object deserialize(Object o, Object... nodes) {
    assert nodes != null;

    if (nodes.length == 0) {
      return XmlSerializerImpl.convert("", myType);
    }

    String value;
    if (nodes.length > 1) {
      value = JDOMUtil.concatTextNodesValues(nodes);
    }
    else {
      assert nodes[0] != null;
      value = JDOMUtil.getValue(nodes[0]);
    }

    return XmlSerializerImpl.convert(value, myType);
  }

  public boolean isBoundTo(Object node) {
    throw new UnsupportedOperationException("Method isBoundTo is not supported in " + getClass());
  }

  public Class getBoundNodeType() {
    return Text.class;
  }

  public void init() {
  }
}
