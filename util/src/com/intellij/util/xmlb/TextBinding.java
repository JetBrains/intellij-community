package com.intellij.util.xmlb;

import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Node;
import org.w3c.dom.Text;

public class TextBinding implements Binding {
  private Accessor myAccessor;
  private XmlSerializerImpl myXmlSerializer;
  private Binding myBinding;

  public TextBinding(final Accessor accessor, final XmlSerializerImpl xmlSerializer) {
    myAccessor = accessor;
    myXmlSerializer = xmlSerializer;
  }

  public Node serialize(Object o, Node context) {
    final Object v = myAccessor.read(o);
    final Node node = myBinding.serialize(v, context);

    return context.getOwnerDocument().createTextNode(node.getTextContent());
  }

  @Nullable
  public Object deserialize(Object context, Node... nodes) {
    assert nodes.length == 1;
    Node node = nodes[0];
    assert isBoundTo(node);

    myAccessor.write(context, myBinding.deserialize(context, nodes[0]));
    return context;
  }

  public boolean isBoundTo(Node node) {
    return node instanceof Text;
  }

  public Class<? extends Node> getBoundNodeType() {
    return Text.class;
  }

  public void init() {
    myBinding = myXmlSerializer.getBinding(myAccessor);
    if (!Text.class.isAssignableFrom(myBinding.getBoundNodeType())) {
      throw new XmlSerializationException("Can't use attribute binding for non-text content: " + myAccessor);
    }
  }
}
