package com.intellij.util.xmlb;

import com.intellij.util.xmlb.annotations.Attribute;
import org.jdom.Content;
import org.jdom.Text;
import org.jetbrains.annotations.Nullable;

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

  public Object serialize(Object o, Object context) {
    final Object v = myAccessor.read(o);
    final Object node = myBinding.serialize(v, context);

    return new org.jdom.Attribute(myAttribute.value(), ((Content)node).getValue());
  }

  @Nullable
  public Object deserialize(Object context, Object... nodes) {
    assert nodes.length == 1;
    Object node = nodes[0];
    assert isBoundTo(node);

    org.jdom.Attribute attr = (org.jdom.Attribute)node;
    final String value = attr.getValue();
    final Text text = new Text(value);
    myAccessor.write(context, myBinding.deserialize(context, text));
    return context;
  }

  public boolean isBoundTo(Object node) {
    return node instanceof org.jdom.Attribute && ((org.jdom.Attribute)node).getName().equals(myAttribute.value());
  }

  public Class getBoundNodeType() {
    return org.jdom.Attribute.class;
  }

  public void init() {
    myBinding = myXmlSerializer.getBinding(myAccessor);
    if (!Text.class.isAssignableFrom(myBinding.getBoundNodeType())) {
      throw new XmlSerializationException("Can't use attribute binding for non-text content: " + myAccessor);
    }
  }
}
