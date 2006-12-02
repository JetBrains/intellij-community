package com.intellij.util.xmlb;

import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

abstract class AbstractCollectionBinding implements Binding {
  private Binding myElementBinding;
  private Class myElementType;
  private XmlSerializerImpl myXmlSerializer;
  private String myTagName;

  public AbstractCollectionBinding(Class elementType, XmlSerializerImpl xmlSerializer, String tagName) {
    myElementType = elementType;
    myXmlSerializer = xmlSerializer;
    myTagName = tagName;
  }

  protected Binding getElementBinding() {
    if (myElementBinding == null) {
      myElementBinding = myXmlSerializer.getBinding(myElementType);

      if (!myElementBinding.getBoundNodeType().isAssignableFrom(Element.class)) {
        myElementBinding = createElementTagWrapper(myElementBinding);
      }
    }

    return myElementBinding;
  }

  protected Binding createElementTagWrapper(final Binding elementBinding) {
    return new TagBindingWrapper(elementBinding, Constants.OPTION, Constants.VALUE);
  }

  abstract Object processResult(List result, Object target);
  abstract Collection getCollection(Object o);

  public Node serialize(Object o, Node context) {
    Collection collection = getCollection(o);
    Document ownerDocument = XmlSerializerImpl.getOwnerDocument(context);

    Node result = getTagName() != null ? ownerDocument.createElement(getTagName()) : ownerDocument.createDocumentFragment();
    for (Object e : collection) {
      result.appendChild(getElementBinding().serialize(e, result));
    }

    return result;
  }

  public Object deserialize(Object o, Node... nodes) {
    List result = new ArrayList();

    if (getTagName() != null) {
      assert nodes.length == 1;
      Element e = (Element)nodes[0];
      NodeList childNodes = e.getChildNodes();
      for (int i = 0; i < childNodes.getLength(); i++) {
        Object v = getElementBinding().deserialize(o, childNodes.item(i));
        //noinspection unchecked
        result.add(v);
      }
    }
    else {
      for (Node node : nodes) {
        Object v = getElementBinding().deserialize(o, node);
        //noinspection unchecked
        result.add(v);
      }
    }


    return processResult(result, o);
  }

  public boolean isBoundTo(Node node) {
    if (!(node instanceof Element)) return false;

    final String tagName = getTagName();
    if (tagName == null) {
      return getElementBinding().isBoundTo(node);
    }

    return node.getNodeName().equals(tagName);
  }

  public Class<? extends Node> getBoundNodeType() {
    return Element.class;
  }


  public Class getElementType() {
    return myElementType;
  }

  @Nullable
  public String getTagName() {
    return myTagName;
  }
}
