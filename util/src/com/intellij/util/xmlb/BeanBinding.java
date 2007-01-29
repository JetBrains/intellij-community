package com.intellij.util.xmlb;

import com.intellij.util.DOMUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.xmlb.annotations.*;
import org.jetbrains.annotations.NonNls;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class BeanBinding implements Binding {
  private String myTagName;
  private Map<Binding, Accessor> myPropertyBindings = new HashMap<Binding, Accessor>();
  private List<Binding> myPropertyBindingsList = new ArrayList<Binding>();
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
  }

  public void init() {
    initPropertyBindings(myBeanClass);
  }


  private void initPropertyBindings(Class<?> beanClass) {
    Accessor[] accessors = getAccessors(beanClass);

    for (Accessor accessor : accessors) {
      final Binding binding = createBindingByAccessor(serializer, accessor);
      myPropertyBindingsList.add(binding);
      myPropertyBindings.put(binding, accessor);
    }
  }

  public Node serialize(Object o, Node context) {
    Document ownerDocument = XmlSerializerImpl.getOwnerDocument(context);
    assert ownerDocument != null;
    Element element = ownerDocument.createElement(myTagName);

    for (Binding binding : myPropertyBindingsList) {
      Accessor accessor = myPropertyBindings.get(binding);
      if (!filter.accepts(accessor, o)) continue;

      //todo: optimize. Cache it.
      final Property property = XmlSerializerImpl.findAnnotation(accessor.getAnnotations(), Property.class);
      if (property != null) {
        try {
          if (!property.filter().newInstance().accepts(accessor, o)) continue;
        }
        catch (InstantiationException e) {
          throw new XmlSerializationException(e);
        }
        catch (IllegalAccessException e) {
          throw new XmlSerializationException(e);
        }
      }

      Node node = binding.serialize(o, element);
      if (node != element) {
        if (node instanceof Attr) {
          Attr attr = (Attr)node;
          element.setAttribute(attr.getName(), attr.getValue());
        }
        else {
          element.appendChild(node);
        }
      }
    }

    return element;
  }


  public void deserializeInto(final Object bean, final Element element) {
    _deserialize(bean, element);
  }

  public Object deserialize(Object o, Node... nodes) {
    return _deserialize(instantiateBean(), nodes);
  }

  private Object _deserialize(final Object result, final Node... nodes) {
    assert nodes.length == 1;
    assert nodes[0] instanceof Element : "Wrong node: " + nodes;
    Element e = (Element)nodes[0];

    ArrayList<Binding> bindings = new ArrayList<Binding>(myPropertyBindings.keySet());


    MultiMap<Binding, Node> data = new MultiMap<Binding, Node>();

    final Node[] children = DOMUtil.getChildNodesWithAttrs(e);
    nextNode: for (Node child : children) {
      if (XmlSerializerImpl.isIgnoredNode(child)) continue;

      for (Binding binding : bindings) {
        if (binding.isBoundTo(child)) {
          data.putValue(binding, child);
          continue nextNode;
        }
      }


      throw new XmlSerializationException("Format error: no binding for " + child + " : " + child.getNodeValue() + " inside " + this);
    }

    for (Object o1 : data.keySet()) {
      Binding binding = (Binding)o1;
      List<Node> nn = data.get(binding);
      binding.deserialize(result, (Node[])nn.toArray(new Node[nn.size()]));
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
    return node instanceof Element && node.getNodeName().equals(myTagName);
  }

  public Class<? extends Node> getBoundNodeType() {
    return Element.class;
  }


  private static String getTagName(Class<?> aClass) {
    Tag tag = aClass.getAnnotation(Tag.class);
    if (tag != null && tag.value().length() != 0) return tag.value();

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


  public String toString() {
    return "BeanBinding[" + myBeanClass.getName() + ", tagName=" + myTagName + "]";
  }

  private static Binding createBindingByAccessor(final XmlSerializerImpl xmlSerializer, Accessor accessor) {
    final Binding binding = _createBinding(accessor, xmlSerializer);
    binding.init();
    return binding;
  }

  private static Binding _createBinding(final Accessor accessor, final XmlSerializerImpl xmlSerializer) {
    Property property = XmlSerializerImpl.findAnnotation(accessor.getAnnotations(), Property.class);
    Tag tag = XmlSerializerImpl.findAnnotation(accessor.getAnnotations(), Tag.class);
    Attribute attribute = XmlSerializerImpl.findAnnotation(accessor.getAnnotations(), Attribute.class);
    Text text = XmlSerializerImpl.findAnnotation(accessor.getAnnotations(), Text.class);

    final Binding binding = xmlSerializer.getTypeBinding(accessor.getGenericType(), accessor);

    if (binding instanceof JDOMElementBinding) return binding;

    if (text != null) return new TextBinding(accessor, xmlSerializer);

    if (attribute != null) {
      return new AttributeBinding(accessor, attribute, xmlSerializer);
    }


    if (tag != null) {
      if (tag.value().length() > 0) return new TagBinding(accessor, tag, xmlSerializer);
    }

    boolean surroundWithTag = true;

    if (property != null) {
      surroundWithTag = property.surroundWithTag();
    }

    if (!surroundWithTag) {
      if (!Element.class.isAssignableFrom(binding.getBoundNodeType())) {
        throw new XmlSerializationException("Text-serializable properties can't be serialized without surrounding tags: " + accessor);
      }
      return new AccessorBindingWrapper(accessor, binding);
    }

    return new OptionTagBinding(accessor, xmlSerializer);
  }

}
