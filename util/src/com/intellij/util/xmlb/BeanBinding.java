package com.intellij.util.xmlb;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.xmlb.annotations.*;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

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
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.xmlb.BeanBinding");

  private String myTagName;
  private Map<Binding, Accessor> myPropertyBindings = new HashMap<Binding, Accessor>();
  private List<Binding> myPropertyBindingsList = new ArrayList<Binding>();
  private Class<?> myBeanClass;
  private SerializationFilter filter;
  private XmlSerializerImpl serializer;
  @NonNls private static final String CLASS_PROPERTY = "class";
  private final Accessor myAccessor;

  public BeanBinding(Class<?> beanClass, XmlSerializerImpl serializer, final Accessor accessor) {
    myAccessor = accessor;
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

  public Object serialize(Object o, Object context) {
    Element element = new Element(myTagName);

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

      Object node = binding.serialize(o, element);
      if (node != element) {
        if (node instanceof org.jdom.Attribute) {
          org.jdom.Attribute attr = (org.jdom.Attribute)node;
          element.setAttribute(attr.getName(), attr.getValue());
        }
        else {
          JDOMUtil.addContent(element, node);
        }
      }
    }

    return element;
  }


  public void deserializeInto(final Object bean, final Element element) {
    _deserializeInto(bean, element);
  }

  public Object deserialize(Object o, Object... nodes) {
    return _deserializeInto(instantiateBean(), nodes);
  }

  private Object _deserializeInto(final Object result, final Object... aNodes) {
    List<Object> nodes = new ArrayList<Object>();
    for (Object aNode : aNodes) {
      if (XmlSerializerImpl.isIgnoredNode(aNode)) continue;
      nodes.add(aNode);
    }

    assert nodes.size() == 1 : "Wrong set of nodes: " + nodes + " for bean"  + myBeanClass + " in " + myAccessor;
    assert nodes.get(0) instanceof Element : "Wrong node: " + nodes;
    Element e = (Element)nodes.get(0);

    ArrayList<Binding> bindings = new ArrayList<Binding>(myPropertyBindings.keySet());


    MultiMap<Binding, Object> data = new MultiMap<Binding, Object>();

    final Object[] children = JDOMUtil.getChildNodesWithAttrs(e);
    nextNode: for (Object child : children) {
      if (XmlSerializerImpl.isIgnoredNode(child)) continue;

      for (Binding binding : bindings) {
        if (binding.isBoundTo(child)) {
          data.putValue(binding, child);
          continue nextNode;
        }
      }

      {
        final String message = "Format error: no binding for " + child + " inside " + this;
        LOG.debug(message);
        Logger.getInstance(myBeanClass.getName()).debug(message);
        Logger.getInstance("#" + myBeanClass.getName()).debug(message);
      }
    }

    for (Object o1 : data.keySet()) {
      Binding binding = (Binding)o1;
      List<Object> nn = data.get(binding);
      binding.deserialize(result, nn.toArray(new Object[nn.size()]));
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

  public boolean isBoundTo(Object node) {
    return node instanceof Element && ((Element)node).getName().equals(myTagName);
  }

  public Class getBoundNodeType() {
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
