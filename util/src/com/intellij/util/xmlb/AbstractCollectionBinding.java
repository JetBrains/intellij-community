package com.intellij.util.xmlb;

import com.intellij.util.xmlb.annotations.AbstractCollection;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

abstract class AbstractCollectionBinding implements Binding {
  private Map<Class, Binding> myElementBindings;

  private Class myElementType;
  private XmlSerializerImpl myXmlSerializer;
  private String myTagName;
  @Nullable private Accessor myAccessor;
  private AbstractCollection myAnnotation = null;
  private boolean myUsingOptionBinding = false;

  public AbstractCollectionBinding(Class elementType, XmlSerializerImpl xmlSerializer, String tagName,
                                   @Nullable Accessor accessor) {
    myElementType = elementType;
    myXmlSerializer = xmlSerializer;
    myTagName = tagName;
    myAccessor = accessor;

    if (accessor != null) {
      myAnnotation = XmlSerializerImpl.findAnnotation(accessor.getAnnotations(), AbstractCollection.class);
    }
  }

  public void init() {
    if (myAnnotation != null) {
      if (!myAnnotation.surroundWithTag()) {
        if (myAnnotation.elementTag() == null) {
          throw new XmlSerializationException("If surround with tag is turned off, element tag must be specified for: " + myAccessor);
        }

        if (myAnnotation.elementTag().equals(Constants.OPTION)) {
          getElementBindings();

          if (myUsingOptionBinding) {
            throw new XmlSerializationException("If surround with tag is turned off, element tag must be specified for: " + myAccessor);
          }
        }
      }
    }
  }

  protected Binding getElementBinding(Class elementClass) {
    final Binding binding = getElementBindings().get(elementClass);
    if (binding == null) throw new XmlSerializationException("Class " + elementClass + " is not bound");
    return binding;
  }

  private Map<Class, Binding> getElementBindings() {
    if (myElementBindings == null) {
      myElementBindings = new HashMap<Class, Binding>();

      myElementBindings.put(myElementType, getBinding(myElementType));

      if (myAnnotation != null) {
        for (Class aClass : myAnnotation.elementTypes()) {
          myElementBindings.put(aClass, getBinding(aClass));

        }
      }
    }

    return myElementBindings;
  }

  protected Binding getElementBinding(Node node) {
    for (Binding binding : getElementBindings().values()) {
      if (binding.isBoundTo(node)) return binding;
    }

    throw new XmlSerializationException("Node " + node + " is not bound");
  }

  private Binding getBinding(final Class type) {
    Binding binding;
    binding = myXmlSerializer.getBinding(type);

    if (!binding.getBoundNodeType().isAssignableFrom(Element.class)) {
      binding = createElementTagWrapper(binding);
      myUsingOptionBinding = true;
    }

    return binding;
  }

  private Binding createElementTagWrapper(final Binding elementBinding) {
    if (myAnnotation == null) return new TagBindingWrapper(elementBinding, Constants.OPTION, Constants.VALUE);

    return new TagBindingWrapper(elementBinding,
                                 myAnnotation.elementTag() != null ? myAnnotation.elementTag() : Constants.OPTION,
                                 myAnnotation.elementValueAttribute() != null ? myAnnotation.elementValueAttribute() : Constants.VALUE);
  }

  abstract Object processResult(List result, Object target);
  abstract Iterable getIterable(Object o);

  public Node serialize(Object o, Node context) {
    Iterable iterable = getIterable(o);
    if (iterable == null) return context;
    Document ownerDocument = XmlSerializerImpl.getOwnerDocument(context);

    Node result = getTagName() != null ? ownerDocument.createElement(getTagName()) : ownerDocument.createDocumentFragment();
    for (Object e : iterable) {
      final Binding binding = getElementBinding(e.getClass());
      result.appendChild(binding.serialize(e, result));
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
        final Node n = childNodes.item(i);
        if (XmlSerializerImpl.isIgnoredNode(n)) continue;
        final Binding elementBinding = getElementBinding(n);
        Object v = elementBinding.deserialize(o, n);
        //noinspection unchecked
        result.add(v);
      }
    }
    else {
      for (Node node : nodes) {
        if (XmlSerializerImpl.isIgnoredNode(node)) continue;
        final Binding elementBinding = getElementBinding(node);
        Object v = elementBinding.deserialize(o, node);
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
      for (Binding binding : getElementBindings().values()) {
        if (binding.isBoundTo(node)) return true;
      }
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
    if (myAnnotation == null || myAnnotation.surroundWithTag()) return myTagName;
    return null;
  }
}
