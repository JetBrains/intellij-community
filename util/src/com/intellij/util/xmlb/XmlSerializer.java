package com.intellij.util.xmlb;

import com.intellij.util.DOMUtil;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.lang.reflect.Array;
import java.net.URL;

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
    catch (XmlSerializationException e) {
      throw e;
    }
    catch (Exception e) {
      throw new XmlSerializationException(e);
    }
  }

  public static <T> T[] deserialize(Element[] elements, Class<T> aClass) throws XmlSerializationException {
    //noinspection unchecked
    T[] result = (T[])Array.newInstance(aClass, elements.length);

    for (int i = 0; i < result.length; i++) {
      result[i] = deserialize(elements[i], aClass);
    }

    return result;
  }

  @Nullable
  public static <T> T deserialize(URL url, Class<T> aClass) throws XmlSerializationException {
    try {
      return deserialize(DOMUtil.load(url).getDocumentElement(), aClass);
    }
    catch (IOException e) {
      throw new XmlSerializationException(e);
    }
    catch (ParserConfigurationException e) {
      throw new XmlSerializationException(e);
    }
    catch (SAXException e) {
      throw new XmlSerializationException(e);
    }
  }
}
