package com.intellij.util.xmlb;

import com.intellij.util.DOMUtil;
import static com.intellij.util.xmlb.Constants.*;
import com.intellij.util.xmlb.annotations.MapAnnotation;
import org.w3c.dom.*;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;

class MapBinding implements Binding {
  private Binding myKeyBinding;
  private Binding myValueBinding;
  private MapAnnotation myMapAnnotation;
  private static final Comparator<Object> KEY_COMPARATOR = new Comparator<Object>() {
    public int compare(final Object o1, final Object o2) {
      if (o1 instanceof Comparable && o2 instanceof Comparable) {
        Comparable c1 = (Comparable)o1;
        Comparable c2 = (Comparable)o2;
        return c1.compareTo(c2);
      }

      return 0;
    }
  };


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

    final Set keySet = map.keySet();
    final Object[] keys = keySet.toArray(new Object[keySet.size()]);
    Arrays.sort(keys, KEY_COMPARATOR);
    for (Object k : keys) {
      Object v = map.get(k);

      Element entry = ownerDocument.createElement(getEntryAttributeName());
      m.appendChild(entry);

      Node kNode = myKeyBinding.serialize(k, entry);

      if (kNode instanceof Text) {
        Text text = (Text)kNode;
        entry.setAttribute(getKeyAttributeValue(), text.getWholeText());
      }
      else {
        if (myMapAnnotation != null && !myMapAnnotation.surroundKeyWithTag()) {
          entry.appendChild(kNode);
        }
        else {
          Element key = ownerDocument.createElement(getKeyAttributeValue());
          entry.appendChild(key);
          key.appendChild(kNode);
        }
      }

      Node vNode = myValueBinding.serialize(v, entry);
      if (vNode instanceof Text) {
        Text text = (Text)vNode;
        entry.setAttribute(getValueAttributeName(), text.getWholeText());
      }
      else {
        if (myMapAnnotation != null && !myMapAnnotation.surroundValueWithTag()) {
          entry.appendChild(vNode);
        }
        else {
          Element value = ownerDocument.createElement(getValueAttributeName());
          entry.appendChild(value);
          value.appendChild(vNode);
        }
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

      Object k = null;
      Object v = null;

      assert entry.getNodeName().equals(getEntryAttributeName());

      Attr keyAttr = entry.getAttributeNode(getKeyAttributeValue());
      if (keyAttr != null) {
        k = myKeyBinding.deserialize(o, keyAttr);
      }
      else {
        if (myMapAnnotation != null && !myMapAnnotation.surroundKeyWithTag()) {
          final Node[] children = DOMUtil.getChildNodes(entry);
          for (Node child : children) {
            if (myKeyBinding.isBoundTo(child)) {
              k = myKeyBinding.deserialize(o, child);
              break;
            }
          }

          assert k != null : "no key found";
        }
        else {
          final Node keyNode = entry.getElementsByTagName(getKeyAttributeValue()).item(0);
          k = myKeyBinding.deserialize(o, DOMUtil.getChildNodes(keyNode));
        }
      }

      Attr valueAttr = entry.getAttributeNode(getValueAttributeName());
      if (valueAttr != null) {
        v = myValueBinding.deserialize(o, valueAttr);
      }
      else {
        if (myMapAnnotation != null && !myMapAnnotation.surroundValueWithTag()) {
          final Node[] children = DOMUtil.getChildNodes(entry);
          for (Node child : children) {
            if (myValueBinding.isBoundTo(child)) {
              v = myValueBinding.deserialize(o, child);
              break;
            }
          }

          assert v != null : "no value found";
        }
        else {
          final Node valueNode = entry.getElementsByTagName(getValueAttributeName()).item(0);
          v = myValueBinding.deserialize(o, DOMUtil.getChildNodes(valueNode));
        }
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
