package com.intellij.util.xmlb;

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
public class XmlSerializer {
    private Document document;

    private XmlSerializer(Document document) {
        this.document = document;
    }

    public static Element serialize(Object object, Document document) throws XmlSerializationException {
        return new XmlSerializer(document).serialize(object);
    }

    private Element serialize(Object object) throws XmlSerializationException {
        try {
            return (Element) getBinding(object.getClass()).serialize(object, document);
        } catch (Exception e) {
            throw new XmlSerializationException(e);
        }
    }

    static Binding getValueBinding(Object value) {
        Class<?> aClass = value.getClass();
        return getBinding(aClass);
    }

    static Binding getBinding(Type type) {
        if (type instanceof Class) {
            //noinspection unchecked
            return _getClassBinding((Class<?>) type, type);
        }
        else if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            Type rawType = parameterizedType.getRawType();
            assert rawType instanceof Class;
            //noinspection unchecked
            return _getClassBinding((Class<?>) rawType, type);
        }

        throw new UnsupportedOperationException("Can't get binding for: " + type);
    }

    private static Binding _getClassBinding(Class<?> aClass, Type originalType) {
        if (aClass.isPrimitive()) return new PrimitiveValueBinding(aClass);
        if (Number.class.isAssignableFrom(aClass)) return new PrimitiveValueBinding(aClass);
        if (String.class.isAssignableFrom(aClass)) return new PrimitiveValueBinding(aClass);
        if (Collection.class.isAssignableFrom(aClass)) return new CollectionBinding((ParameterizedType) originalType);
        if (Map.class.isAssignableFrom(aClass)) return new MapBinding((ParameterizedType) originalType);

        return new BeanToTagBinding(aClass);
    }

    @SuppressWarnings({"unchecked"})
    private static <T> T findAnnotation(Annotation[] annotations, Class<T> aClass) {
        if (annotations == null) return null;

        for (Annotation annotation : annotations) {
            if (aClass.isAssignableFrom(annotation.getClass())) return (T) annotation;
        }
        return null;
    }

    @SuppressWarnings({"unchecked"})
    public static <T> T deserialize(Element element, Class<T> aClass) throws XmlSerializationException {
        try {
            return (T) getBinding(aClass).deserialize(null, element);
        }
        catch (Exception e) {
            throw new XmlSerializationException(e);
        }
    }

    @SuppressWarnings({"unchecked"})
    static <T> T convert(Object value, Class<T> type) {
        if (value == null) return null;
        if (type.isInstance(value)) return (T) value;
        if (String.class.isAssignableFrom(type)) return (T) String.valueOf(value);
        if (int.class.isAssignableFrom(type) || Integer.class.isAssignableFrom(type)) return (T) Integer.valueOf(String.valueOf(value));
        if (double.class.isAssignableFrom(type) || Double.class.isAssignableFrom(type)) return (T) Double.valueOf(String.valueOf(value));
        if (float.class.isAssignableFrom(type) || Float.class.isAssignableFrom(type)) return (T) Float.valueOf(String.valueOf(value));
        if (long.class.isAssignableFrom(type) || Long.class.isAssignableFrom(type)) return (T) Long.valueOf(String.valueOf(value));
        
        throw new XmlSerializationException("Can't covert " + value.getClass() + " into " + type);
    }

    static Binding createBindingByAccessor(Accessor accessor) {
        Tag tag = findAnnotation(accessor.getAnnotations(), Tag.class);
        if (tag != null) return new TagBinding(accessor, tag);
        return new OptionTagBinding(accessor);
    }


    static Document getOwnerDocument(Node context) {
        if (context instanceof Document) return (Document) context;
        return context.getOwnerDocument();
    }

}
