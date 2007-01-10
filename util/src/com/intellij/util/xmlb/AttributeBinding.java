package com.intellij.util.xmlb;

import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Attr;
import org.w3c.dom.Node;
import org.w3c.dom.Text;

public class AttributeBinding implements Binding {
  private Accessor myAccessor;
  private Attribute myAttribute;
  private XmlSerializerImpl myXmlSerializer;
  private Binding myBinding;

  public AttributeBinding(final Accessor accessor, final Attribute attribute, final XmlSerializerImpl xmlSerializer) {
    myAccessor = accessor;
    myAttribute = attribute;
    myXmlSerializer = xmlSerializer;
  }

  public Node serialize(Object o, Node context) {
    final Object v = myAccessor.read(o);
    final Node node = myBinding.serialize(v, context);

    final Attr attr = context.getOwnerDocument().createAttribute(myAttribute.value());
    attr.setValue(node.getTextContent());
    return attr;
  }

  @Nullable
  public Object deserialize(Object context, Node... nodes) {
    assert nodes.length == 1;
    Node node = nodes[0];
    assert isBoundTo(node);

    Attr attr = (Attr)node;
    final String value = attr.getValue();
    final Text text = node.getOwnerDocument().createTextNode(value);
    myAccessor.write(context, myBinding.deserialize(context, text));
    return context;
  }

  public boolean isBoundTo(Node node) {
    return node instanceof Attr && node.getNodeName().equals(myAttribute.value());
  }

  public Class<? extends Node> getBoundNodeType() {
    return Attr.class;
  }

  public void init() {
    myBinding = myXmlSerializer.getBinding(myAccessor);
    if (!Text.class.isAssignableFrom(myBinding.getBoundNodeType())) {
      throw new XmlSerializationException("Can't use attribute binding for non-text content: " + myAccessor);
    }
  }
}
