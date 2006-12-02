package com.intellij.util.xmlb;

import static com.intellij.util.xmlb.Constants.*;
import org.w3c.dom.*;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;

class MapBinding implements Binding {
  private Binding keyBinding;
  private Binding valueBinding;


  public MapBinding(ParameterizedType type, XmlSerializerImpl serializer) {
    Type[] arguments = type.getActualTypeArguments();
    Type keyType = arguments[0];
    Type valueType = arguments[1];

    keyBinding = serializer.getBinding(keyType);
    valueBinding = serializer.getBinding(valueType);
  }

  public Node serialize(Object o, Node context) {
    Map map = (Map)o;

    Document ownerDocument = XmlSerializerImpl.getOwnerDocument(context);

    Element m = ownerDocument.createElement(Constants.MAP);

    for (Object k : map.keySet()) {
      Object v = map.get(k);

      Element entry = ownerDocument.createElement(ENTRY);
      m.appendChild(entry);

      Node kNode = keyBinding.serialize(k, entry);
      Node vNode = valueBinding.serialize(v, entry);

      if (kNode instanceof Text) {
        Text text = (Text)kNode;
        entry.setAttribute(KEY, text.getWholeText());
      }
      else {
        Element key = ownerDocument.createElement(KEY);
        entry.appendChild(key);
        key.appendChild(kNode);
      }

      if (vNode instanceof Text) {
        Text text = (Text)vNode;
        entry.setAttribute(VALUE, text.getWholeText());
      }
      else {
        Element value = ownerDocument.createElement(VALUE);
        entry.appendChild(value);
        value.appendChild(vNode);
      }
    }

    return m;
  }

  public Object deserialize(Object o, Node... nodes) {
    Map map = (Map)o;
    map.clear();

    assert nodes.length == 1;
    Element m = (Element)nodes[0];

    NodeList list = m.getChildNodes();
    for (int i = 0; i < list.getLength(); i++) {
      Element entry = (Element)list.item(i);

      Object k;
      Object v;

      assert entry.getNodeName().equals(ENTRY);

      Attr keyAttr = entry.getAttributeNode(KEY);
      if (keyAttr != null) {
        k = keyBinding.deserialize(o, keyAttr);
      }
      else {
        k = keyBinding.deserialize(o, entry.getElementsByTagName(KEY).item(0));
      }

      Attr valueAttr = entry.getAttributeNode(VALUE);
      if (valueAttr != null) {
        v = keyBinding.deserialize(o, valueAttr);
      }
      else {
        v = keyBinding.deserialize(o, entry.getElementsByTagName(VALUE).item(0));
      }

      //noinspection unchecked
      map.put(k, v);
    }

    return map;
  }

  public boolean isBoundTo(Node node) {
    throw new UnsupportedOperationException("Method isBoundTo is not supported in " + getClass());
  }

  public Class<? extends Node> getBoundNodeType() {
    throw new UnsupportedOperationException("Method getBoundNodeType is not supported in " + getClass());
  }
}
