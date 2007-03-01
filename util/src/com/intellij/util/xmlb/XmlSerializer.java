package com.intellij.util.xmlb;

import com.intellij.openapi.util.JDOMUtil;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.Nullable;

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

  public static Element serialize(Object object) throws XmlSerializationException {
    return serialize(object, TRUE_FILTER);
  }

  public static Element serialize(Object object, SerializationFilter filter) throws XmlSerializationException {
    if (filter == null) filter = TRUE_FILTER;
    return new XmlSerializerImpl(filter).serialize(object);
  }

  @Nullable
  public static <T> T deserialize(Document document, Class<T> aClass) throws XmlSerializationException {
    return deserialize(document.getRootElement(), aClass);
  }

  @Nullable
  @SuppressWarnings({"unchecked"})
  public static <T> T deserialize(Element element, Class<T> aClass) throws XmlSerializationException {
    try {
      XmlSerializerImpl serializer = new XmlSerializerImpl(TRUE_FILTER);
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
      Document document = JDOMUtil.loadDocument(url);
      document = JDOMXIncluder.resolve(document, url.toExternalForm());
      return deserialize(document.getRootElement(), aClass);
    }
    catch (IOException e) {
      throw new XmlSerializationException(e);
    }
    catch (JDOMException e) {
      throw new XmlSerializationException(e);
    }
  }

  public static void deserializeInto(final Object bean, final Element element) {
    try {
      XmlSerializerImpl serializer = new XmlSerializerImpl(TRUE_FILTER);
      final Binding binding = serializer.getBinding(bean.getClass());
      assert binding instanceof BeanBinding;

      ((BeanBinding)binding).deserializeInto(bean, element);
    }
    catch (XmlSerializationException e) {
      throw e;
    }
    catch (Exception e) {
      throw new XmlSerializationException(e);
    }
  }
}
