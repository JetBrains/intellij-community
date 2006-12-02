package com.intellij.util.xmlb;

import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Map;

/**
 * @author mike
 */
class XmlSerializerImpl {

  private Document document;
  private SerializationFilter filter;


  public XmlSerializerImpl(Document document, SerializationFilter filter) {
    this.document = document;
    this.filter = filter;
  }

  Element serialize(Object object) throws XmlSerializationException {
    try {
      return (Element)getBinding(object.getClass()).serialize(object, document);
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

  private Binding getTypeBinding(Type type, Accessor accessor) {
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
    if (aClass.isPrimitive()) return new PrimitiveValueBinding(aClass);
    if (aClass.isArray()) return new ArrayBinding(this, aClass, accessor);
    if (Number.class.isAssignableFrom(aClass)) return new PrimitiveValueBinding(aClass);
    if (String.class.isAssignableFrom(aClass)) return new PrimitiveValueBinding(aClass);
    if (Collection.class.isAssignableFrom(aClass)) return new CollectionBinding((ParameterizedType)originalType, this);
    if (Map.class.isAssignableFrom(aClass)) return new MapBinding((ParameterizedType)originalType, this);

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

    throw new XmlSerializationException("Can't covert " + value.getClass() + " into " + type);
  }

  Binding createBindingByAccessor(Accessor accessor) {
    Property tag = findAnnotation(accessor.getAnnotations(), Property.class);
    if (tag != null) {
      if (tag.tagName().length() > 0) return new TagBinding(accessor, tag, this);
      if (!tag.surroundWithTag()) {
        final Binding binding = getTypeBinding(accessor.getGenericType(), accessor);
        if (!Element.class.isAssignableFrom(binding.getBoundNodeType())) {
          throw new XmlSerializationException("Text-serializable properties can't be serialized without surrounding tags: " + accessor);
        }
        return new AccessorBindingWrapper(accessor, binding);
      }
    }

    return new OptionTagBinding(accessor, this);
  }


  static Document getOwnerDocument(Node context) {
    if (context instanceof Document) return (Document)context;
    return context.getOwnerDocument();
  }

  public SerializationFilter getFilter() {
    return filter;
  }
}
