package com.intellij.util.xmlb;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

class BeanToTagBinding implements Binding {
    private String myTagName;
    private Binding[] myPropertyBindings;
    private Class<?> myBeanClass;

    public BeanToTagBinding(Class<?> beanClass) {
        this.myBeanClass = beanClass;
        myTagName = getTagName(beanClass);
        myPropertyBindings = getPropertyBindings(beanClass);
    }

    private Binding[] getPropertyBindings(Class<?> beanClass) {
        List<Binding> bindings = new ArrayList<Binding>();
        Accessor[] accessors = getAccessors(beanClass);

        for (Accessor accessor : accessors) {
            bindings.add(accessor.createBinding());
        }

        return bindings.toArray(new Binding[bindings.size()]);

    }

    public Node serialize(Object o, Node context) {
        Document ownerDocument = XmlSerializer.getOwnerDocument(context);
        assert ownerDocument != null;
        Element element = ownerDocument.createElement(myTagName);

        for (Binding binding : myPropertyBindings) {
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
        Element e = (Element) node;

        ArrayList<Binding> bindings = new ArrayList<Binding>(Arrays.asList(myPropertyBindings));

        NodeList childNodes = e.getChildNodes();
        nextNode: for (int i = 0; i < childNodes.getLength(); i++) {
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
        } catch (InstantiationException e) {
            throw new XmlSerializationException(e);
        } catch (IllegalAccessException e) {
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
                if (propertyDescriptor.getName().equals("class")) continue;

                accessors.add(new PropertyAccessor(propertyDescriptor));
            }

            Field[] fields = aClass.getFields();
            for (Field field : fields) {
                int modifiers = field.getModifiers();
                if (Modifier.isPublic(modifiers) && !Modifier.isStatic(modifiers)) {
                    accessors.add(new FieldAccessor(field));
                }
            }


            return accessors.toArray(new Accessor[accessors.size()]);
        } catch (IntrospectionException e) {
            throw new XmlSerializationException(e);
        }
    }
}
