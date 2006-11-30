package com.intellij.util.xmlb;

import org.jetbrains.annotations.NonNls;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

class BeanBinding implements Binding {
  private String myTagName;
  private Map<Binding, Accessor> myPropertyBindings = new HashMap<Binding, Accessor>();
  private Class<?> myBeanClass;
  private SerializationFilter filter;
  private XmlSerializerImpl serializer;
  @NonNls private static final String CLASS_PROPERTY = "class";

  public BeanBinding(Class<?> beanClass, XmlSerializerImpl serializer) {
    assert !beanClass.isArray() : "Bean is an array";
    assert !beanClass.isPrimitive() : "Bean is primitive type";
    myBeanClass = beanClass;
    filter = serializer.getFilter();
    this.serializer = serializer;
    myTagName = getTagName(beanClass);
    initPropertyBindings(beanClass);
  }

  private void initPropertyBindings(Class<?> beanClass) {
    Accessor[] accessors = getAccessors(beanClass);

    for (Accessor accessor : accessors) {
      myPropertyBindings.put(serializer.createBindingByAccessor(accessor), accessor);
    }
  }

  public Node serialize(Object o, Node context) {
    Document ownerDocument = XmlSerializerImpl.getOwnerDocument(context);
    assert ownerDocument != null;
    Element element = ownerDocument.createElement(myTagName);

    ArrayList<Binding> bindings = new ArrayList<Binding>(myPropertyBindings.keySet());

    Collections.sort(bindings, new Comparator<Binding>() {
      public int compare(Binding b1, Binding b2) {
        Accessor a1 = myPropertyBindings.get(b1);
        Accessor a2 = myPropertyBindings.get(b2);
        return a1.getName().compareTo(a2.getName());
      }
    });

    for (Binding binding : bindings) {
      Accessor accessor = myPropertyBindings.get(binding);
      if (!filter.accepts(accessor, o)) continue;

      Node node = binding.serialize(o, element);
      if (node != element) {
        element.appendChild(node);
      }
    }

    return element;
  }

  public Object deserialize(Object o, Node node) {
    Object result = instantiateBean();

    assert node instanceof Element : "Wrong node: " + node;
    Element e = (Element)node;

    ArrayList<Binding> bindings = new ArrayList<Binding>(myPropertyBindings.keySet());

    NodeList childNodes = e.getChildNodes();
    nextNode:
    for (int i = 0; i < childNodes.getLength(); i++) {
      Node child = childNodes.item(i);

      for (Iterator<Binding> j = bindings.iterator(); j.hasNext();) {
        Binding binding = j.next();
        if (binding.isBoundTo(child)) {
          j.remove();
          binding.deserialize(result, child);
          continue nextNode;
        }
      }

      throw new XmlSerializationException("Format error: no binding for " + child);
    }

    return result;
  }

  private Object instantiateBean() {
    Object result;

    try {
      result = myBeanClass.newInstance();
    }
    catch (InstantiationException e) {
      throw new XmlSerializationException(e);
    }
    catch (IllegalAccessException e) {
      throw new XmlSerializationException(e);
    }
    return result;
  }

  public boolean isBoundTo(Node node) {
    throw new UnsupportedOperationException("Method isBoundTo is not supported in " + getClass());
  }

  public Class<? extends Node> getBoundNodeType() {
    throw new UnsupportedOperationException("Method getBoundNodeType is not supported in " + getClass());
  }

  private static String getTagName(Class<?> aClass) {
    Tag tag = aClass.getAnnotation(Tag.class);
    if (tag != null) return tag.name();

    return aClass.getSimpleName();
  }

  static Accessor[] getAccessors(Class<?> aClass) {
    try {
      List<Accessor> accessors = new ArrayList<Accessor>();

      BeanInfo info = Introspector.getBeanInfo(aClass);

      PropertyDescriptor[] propertyDescriptors = info.getPropertyDescriptors();
      for (PropertyDescriptor propertyDescriptor : propertyDescriptors) {
        if (propertyDescriptor.getName().equals(CLASS_PROPERTY)) continue;
        final Method readMethod = propertyDescriptor.getReadMethod();
        final Method writeMethod = propertyDescriptor.getWriteMethod();

        if (readMethod == null) continue;
        if (writeMethod == null) continue;

        if (XmlSerializerImpl.findAnnotation(readMethod.getAnnotations(), Transient.class) != null ||
            XmlSerializerImpl.findAnnotation(writeMethod.getAnnotations(), Transient.class) != null) continue;

        accessors.add(new PropertyAccessor(propertyDescriptor));
      }

      Field[] fields = aClass.getFields();
      for (Field field : fields) {
        int modifiers = field.getModifiers();
        if (Modifier.isPublic(modifiers) && !Modifier.isStatic(modifiers) && XmlSerializerImpl.findAnnotation(field.getAnnotations(), Transient.class) == null) {
          accessors.add(new FieldAccessor(field));
        }
      }


      return accessors.toArray(new Accessor[accessors.size()]);
    }
    catch (IntrospectionException e) {
      throw new XmlSerializationException(e);
    }
  }
}
