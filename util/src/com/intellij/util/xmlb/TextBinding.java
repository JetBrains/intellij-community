package com.intellij.util.xmlb;

import org.jdom.Content;
import org.jdom.Text;
import org.jetbrains.annotations.Nullable;

public class TextBinding implements Binding {
  private Accessor myAccessor;
  private XmlSerializerImpl myXmlSerializer;
  private Binding myBinding;

  public TextBinding(final Accessor accessor, final XmlSerializerImpl xmlSerializer) {
    myAccessor = accessor;
    myXmlSerializer = xmlSerializer;
  }

  public Object serialize(Object o, Object context) {
    final Object v = myAccessor.read(o);
    final Object node = myBinding.serialize(v, context);

    return new Text(((Content)node).getValue());
  }

  @Nullable
  public Object deserialize(Object context, Object... nodes) {
    assert nodes.length == 1;
    Object node = nodes[0];
    assert isBoundTo(node);

    myAccessor.write(context, myBinding.deserialize(context, nodes[0]));
    return context;
  }

  public boolean isBoundTo(Object node) {
    return node instanceof Text;
  }

  public Class getBoundNodeType() {
    return Text.class;
  }

  public void init() {
    myBinding = myXmlSerializer.getBinding(myAccessor);
    if (!Text.class.isAssignableFrom(myBinding.getBoundNodeType())) {
      throw new XmlSerializationException("Can't use attribute binding for non-text content: " + myAccessor);
    }
  }
}
