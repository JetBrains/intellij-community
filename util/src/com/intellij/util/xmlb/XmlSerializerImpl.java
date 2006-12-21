package com.intellij.util.xmlb;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.*;

import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * @author mike
 */
class XmlSerializerImpl {

  private Document document;
  private SerializationFilter filter;
  private Map<Pair<Class, Accessor>, Binding> myBindings = new HashMap<Pair<Class, Accessor>, Binding>();


  public XmlSerializerImpl(Document document, SerializationFilter filter) {
    this.document = document;
    this.filter = filter;
  }

  Element serialize(Object object) throws XmlSerializationException {
    try {
      return (Element)getBinding(object.getClass()).serialize(object, document);
    }
    catch (XmlSerializationException e) {
      throw e;
    }
    catch (Exception e) {
      throw new XmlSerializationException(e);
    }
  }

  Binding getBinding(Type type) {
    return getTypeBinding(type, null);
  }

  Binding getBinding(Accessor accessor) {
    return getTypeBinding(accessor.getGenericType(), accessor);
  }

  Binding getTypeBinding(Type type, Accessor accessor) {
    if (type instanceof Class) {
      //noinspection unchecked
      return _getClassBinding((Class<?>)type, type, accessor);
    }
    else if (type instanceof ParameterizedType) {
      ParameterizedType parameterizedType = (ParameterizedType)type;
      Type rawType = parameterizedType.getRawType();
      assert rawType instanceof Class;
      //noinspection unchecked
      return _getClassBinding((Class<?>)rawType, type, accessor);
    }

    throw new UnsupportedOperationException("Can't get binding for: " + type);
  }


  private Binding _getClassBinding(Class<?> aClass, Type originalType, final Accessor accessor) {
    final Pair<Class, Accessor> p = new Pair<Class, Accessor>(aClass, accessor);

    Binding binding = myBindings.get(p);

    if (binding == null) {
      binding = _getNonCachedClassBinding(aClass, accessor, originalType);
      myBindings.put(p, binding);
      binding.init();
    }

    return binding;
  }

  private Binding _getNonCachedClassBinding(final Class<?> aClass, final Accessor accessor, final Type originalType) {
    if (aClass.isPrimitive()) return new PrimitiveValueBinding(aClass);
    if (aClass.isArray()) return new ArrayBinding(this, aClass, accessor);
    if (Number.class.isAssignableFrom(aClass)) return new PrimitiveValueBinding(aClass);
    if (String.class.isAssignableFrom(aClass)) return new PrimitiveValueBinding(aClass);
    if (Collection.class.isAssignableFrom(aClass)) return new CollectionBinding((ParameterizedType)originalType, this, accessor);
    if (Map.class.isAssignableFrom(aClass)) return new MapBinding((ParameterizedType)originalType, this, accessor);

    return new BeanBinding(aClass, this);
  }

  @Nullable
  @SuppressWarnings({"unchecked"})
  static <T> T findAnnotation(Annotation[] annotations, Class<T> aClass) {
    if (annotations == null) return null;

    for (Annotation annotation : annotations) {
      if (aClass.isAssignableFrom(annotation.getClass())) return (T)annotation;
    }
    return null;
  }

  @Nullable
  @SuppressWarnings({"unchecked"})
  static <T> T convert(Object value, Class<T> type) {
    if (value == null) return null;
    if (type.isInstance(value)) return (T)value;
    if (String.class.isAssignableFrom(type)) return (T)String.valueOf(value);
    if (int.class.isAssignableFrom(type) || Integer.class.isAssignableFrom(type)) return (T)Integer.valueOf(String.valueOf(value));
    if (double.class.isAssignableFrom(type) || Double.class.isAssignableFrom(type)) return (T)Double.valueOf(String.valueOf(value));
    if (float.class.isAssignableFrom(type) || Float.class.isAssignableFrom(type)) return (T)Float.valueOf(String.valueOf(value));
    if (long.class.isAssignableFrom(type) || Long.class.isAssignableFrom(type)) return (T)Long.valueOf(String.valueOf(value));
    if (boolean.class.isAssignableFrom(type) || Boolean.class.isAssignableFrom(type)) return (T)Boolean.valueOf(String.valueOf(value));

    throw new XmlSerializationException("Can't covert " + value.getClass() + " into " + type);
  }


  static Document getOwnerDocument(Node context) {
    if (context instanceof Document) return (Document)context;
    return context.getOwnerDocument();
  }

  public SerializationFilter getFilter() {
    return filter;
  }

  static boolean isIgnoredNode(final Node node) {
    if (node instanceof Text && node.getNodeValue().trim().length() == 0) {
      return true;
    }
    if (node instanceof Comment) {
      return true;
    }

    return false;
  }
}
