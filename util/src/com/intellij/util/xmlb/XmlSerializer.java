package com.intellij.util.xmlb;

import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class XmlSerializer {
  private static final SerializationFilter TRUE_FILTER = new SerializationFilter() {
    public boolean accepts(Accessor accessor, Object bean) {
      return true;
    }
  };

  private XmlSerializer() {
  }

  public static Element serialize(Object object, Document document) throws XmlSerializationException {
    return serialize(object, document, TRUE_FILTER);
  }

  public static Element serialize(Object object, Document document, SerializationFilter filter) throws XmlSerializationException {
    if (filter == null) filter = TRUE_FILTER;
    return new XmlSerializerImpl(document, filter).serialize(object);
  }

  @Nullable
  @SuppressWarnings({"unchecked"})
  public static <T> T deserialize(Element element, Class<T> aClass) throws XmlSerializationException {
    try {
      XmlSerializerImpl serializer = new XmlSerializerImpl(element.getOwnerDocument(), TRUE_FILTER);
      return (T)serializer.getBinding(aClass).deserialize(null, element);
    }
    catch (Exception e) {
      throw new XmlSerializationException(e);
    }
  }
}
