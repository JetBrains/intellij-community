package com.intellij.util.xmlb;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.Text;

//todo: merge with option tag binding
class OptionTagBindingWrapper implements Binding {
    private Binding binding;

    public OptionTagBindingWrapper(Binding binding) {
        this.binding = binding;

        assert binding.getBoundNodeType().isAssignableFrom(Text.class);
    }

    public Node serialize(Object o, Node context) {
        Document ownerDocument = XmlSerializer.getOwnerDocument(context);
        Element e = ownerDocument.createElement(Constants.OPTION);
        Node n = binding.serialize(o, e);
        e.setAttribute(Constants.VALUE, n.getNodeValue());
        return e;
    }

    public Object deserialize(Object context, Node node) {
        Element e = (Element) node;
        return binding.deserialize(context, e.getAttributeNode(Constants.VALUE));
    }

    public boolean isBoundTo(Node node) {
        throw new UnsupportedOperationException("Method isBoundTo is not supported in " + getClass());
    }

    public Class<? extends Node> getBoundNodeType() {
        throw new UnsupportedOperationException("Method getBoundNodeType is not supported in " + getClass());
    }
}
