package com.intellij.util.xmlb;

import com.intellij.util.DOMUtil;
import static com.intellij.util.xmlb.Constants.*;
import com.intellij.util.xmlb.annotations.MapAnnotation;
import org.w3c.dom.*;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;

class MapBinding implements Binding {
  private Binding myKeyBinding;
  private Binding myValueBinding;
  private MapAnnotation myMapAnnotation;


  public MapBinding(ParameterizedType type, XmlSerializerImpl serializer, Accessor accessor) {
    Type[] arguments = type.getActualTypeArguments();
    Type keyType = arguments[0];
    Type valueType = arguments[1];

    myKeyBinding = serializer.getBinding(keyType);
    myValueBinding = serializer.getBinding(valueType);
    myMapAnnotation = XmlSerializerImpl.findAnnotation(accessor.getAnnotations(), MapAnnotation.class);
  }

  public Node serialize(Object o, Node context) {
    Map map = (Map)o;

    Document ownerDocument = XmlSerializerImpl.getOwnerDocument(context);

    Element m;

    if (myMapAnnotation == null || myMapAnnotation.surroundWithTag()) {
      m = ownerDocument.createElement(Constants.MAP);
    }
    else {
      m = (Element)context;
    }

    for (Object k : map.keySet()) {
      Object v = map.get(k);

      Element entry = ownerDocument.createElement(getEntryAttributeName());
      m.appendChild(entry);

      Node kNode = myKeyBinding.serialize(k, entry);
      Node vNode = myValueBinding.serialize(v, entry);

      if (kNode instanceof Text) {
        Text text = (Text)kNode;
        entry.setAttribute(getKeyAttributeValue(), text.getWholeText());
      }
      else {
        Element key = ownerDocument.createElement(getKeyAttributeValue());
        entry.appendChild(key);
        key.appendChild(kNode);
      }

      if (vNode instanceof Text) {
        Text text = (Text)vNode;
        entry.setAttribute(getValueAttributeName(), text.getWholeText());
      }
      else {
        Element value = ownerDocument.createElement(getValueAttributeName());
        entry.appendChild(value);
        value.appendChild(vNode);
      }
    }

    return m;
  }

  private String getEntryAttributeName() {
    return myMapAnnotation == null ? ENTRY : myMapAnnotation.entryTagName();
  }

  private String getValueAttributeName() {
    return myMapAnnotation == null ? VALUE : myMapAnnotation.valueAttributeName();
  }

  private String getKeyAttributeValue() {
    return myMapAnnotation == null ? KEY : myMapAnnotation.keyAttributeName();
  }

  public Object deserialize(Object o, Node... nodes) {
    Map map = (Map)o;
    map.clear();

    final Node[] childNodes;

    if (myMapAnnotation == null || myMapAnnotation.surroundWithTag()) {
      assert nodes.length == 1;
      Element m = (Element)nodes[0];
      childNodes = DOMUtil.getChildNodes(m);
    }
    else {
      childNodes = nodes;
    }


    for (Node childNode : childNodes) {
      if (XmlSerializerImpl.isIgnoredNode(childNode)) continue;
      
      Element entry = (Element)childNode;

      Object k;
      Object v;

      assert entry.getNodeName().equals(getEntryAttributeName());

      Attr keyAttr = entry.getAttributeNode(getKeyAttributeValue());
      if (keyAttr != null) {
        k = myKeyBinding.deserialize(o, keyAttr);
      }
      else {
        k = myKeyBinding.deserialize(o, entry.getElementsByTagName(getKeyAttributeValue()).item(0));
      }

      Attr valueAttr = entry.getAttributeNode(getValueAttributeName());
      if (valueAttr != null) {
        v = myValueBinding.deserialize(o, valueAttr);
      }
      else {
        v = myValueBinding.deserialize(o, entry.getElementsByTagName(getValueAttributeName()).item(0));
      }

      //noinspection unchecked
      map.put(k, v);
    }

    return map;
  }

  public boolean isBoundTo(Node node) {
    if (myMapAnnotation != null && !myMapAnnotation.surroundWithTag()) {
      return myMapAnnotation.entryTagName().equals(node.getNodeName());
    }

    return node.getNodeName().equals(Constants.MAP);
  }

  public Class<? extends Node> getBoundNodeType() {
    return Element.class;
  }

  public void init() {
  }
}
