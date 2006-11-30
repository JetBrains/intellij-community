package com.intellij.util.xmlb;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

class TagBinding implements Binding {
    private Accessor accessor;
    private String myTagName;
    private Binding binding;

    public TagBinding(Accessor accessor, Tag tagAnnotation, XmlSerializer xmlSerializer) {
        this.accessor = accessor;
        myTagName = tagAnnotation.name();
        binding = xmlSerializer.getBinding(accessor.getValueClass());
    }

    public Node serialize(Object o, Node context) {
        Document ownerDocument = XmlSerializer.getOwnerDocument(context);
        Object value = accessor.read(o);
        if (value == null) return context;

        Element v = ownerDocument.createElement(myTagName);

        Node node = binding.serialize(value, v);
        v.appendChild(node);

        return v;
    }

    public Object deserialize(Object o, Node node) {
        Object v = binding.deserialize(o, node.getChildNodes().item(0));
        Object value = XmlSerializer.convert(v, accessor.getValueClass());
        accessor.write(o, value);
        return o;
    }

    public boolean isBoundTo(Node node) {
        return node instanceof Element && node.getNodeName().equals(myTagName);
    }

    public Class<? extends Node> getBoundNodeType() {
        throw new UnsupportedOperationException("Method getBoundNodeType is not supported in " + getClass());
    }
}
