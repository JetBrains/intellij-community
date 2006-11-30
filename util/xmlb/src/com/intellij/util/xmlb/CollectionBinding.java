package com.intellij.util.xmlb;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;

class CollectionBinding implements Binding {
    private Binding elementBinding;

    public CollectionBinding(ParameterizedType type, XmlSerializer xmlSerializer) {
        Type[] arguments = type.getActualTypeArguments();
        Type elementType = arguments[0];

        elementBinding = xmlSerializer.getBinding(elementType);

        if (!elementBinding.getBoundNodeType().isAssignableFrom(Element.class)) {
            elementBinding = new OptionTagBindingWrapper(elementBinding);
        }
    }

    public Node serialize(Object o, Node context) {
        Collection collection = (Collection) o;
        Document ownerDocument = XmlSerializer.getOwnerDocument(context);

        Element c = ownerDocument.createElement(Constants.COLLECTION);
        for (Object e : collection) {
            c.appendChild(elementBinding.serialize(e, c));
        }

        return c;
    }

    public Object deserialize(Object o, Node node) {
        Collection c = (Collection) o;
        c.clear();
        Element e = (Element) node;
        NodeList childNodes = e.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Object v = elementBinding.deserialize(o, childNodes.item(i));
            //noinspection unchecked
            c.add(v);
        }

        return c;
    }

    public boolean isBoundTo(Node node) {
        return node instanceof Element && node.getNodeName().equals(Constants.COLLECTION);
    }

    public Class<? extends Node> getBoundNodeType() {
        throw new UnsupportedOperationException("Method getBoundNodeType is not supported in " + getClass());
    }
}
