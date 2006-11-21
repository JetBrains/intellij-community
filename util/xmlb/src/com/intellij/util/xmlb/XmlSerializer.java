package com.intellij.util.xmlb;

import org.w3c.dom.*;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * @author mike
 */
public class XmlSerializer {
    private static final String OPTION = "option";
    private static final String VALUE = "value";
    private Document document;


    private XmlSerializer(Document document) {
        this.document = document;
    }

    public static Element serialize(Object object, Document document) throws XmlSerializationException {
        return new XmlSerializer(document).serialize(object);
    }

    private Element serialize(Object object) throws XmlSerializationException {
        try {
            return (Element) getValueBinding(object).serialize(object, document);
        } catch (Exception e) {
            throw new XmlSerializationException(e);
        }
    }

    private static Binding getValueBinding(Object value) throws Exception {
        Class<? extends Object> aClass = value.getClass();
        return getClassBinding(aClass);
    }

    private static Binding getClassBinding(Class<? extends Object> aClass) throws Exception {
        if (aClass.isPrimitive()) return new PrimitiveValueBinding();
        if (Number.class.isAssignableFrom(aClass)) return new PrimitiveValueBinding();
        if (String.class.isAssignableFrom(aClass)) return new PrimitiveValueBinding();
        if (Collection.class.isAssignableFrom(aClass)) return new CollectionBinding();
        if (Map.class.isAssignableFrom(aClass)) return new MapBinding();

        return new BeanToTagBinding(aClass);
    }

    private static <T> T findAnnotation(Annotation[] annotations, Class<T> aClass) {
        if (annotations == null) return null;

        for (Annotation annotation : annotations) {
            if (aClass.isAssignableFrom(annotation.getClass())) return (T) annotation;
        }
        return null;
    }

    private interface Accessor {
        Object read(Object o) throws Exception;

        Annotation[] getAnnotations() throws Exception;

        String getName();

        Binding createBinding() throws Exception;
    }

    private static class FieldAccessor implements Accessor {
        private final Field myField;


        public FieldAccessor(Field myField) {
            this.myField = myField;
        }

        public Object read(Object o) throws Exception {
            return myField.get(o);
        }

        public Annotation[] getAnnotations() throws Exception {
            return myField.getAnnotations();
        }

        public String getName() {
            return myField.getName();
        }

        public Binding createBinding() throws Exception {
            return createBindingByAccessor(this);
        }
    }

    private static Binding createBindingByAccessor(Accessor accessor) throws Exception {
        Tag tag = findAnnotation(accessor.getAnnotations(), Tag.class);
        if (tag != null) return new TagBinding(accessor, tag);
        return new OptionTagBinding(accessor);
    }

    private static class PropertyAccessor implements Accessor {
        private final PropertyDescriptor myPropertyDescriptor;


        public PropertyAccessor(PropertyDescriptor myPropertyDescriptor) {
            this.myPropertyDescriptor = myPropertyDescriptor;
        }


        public Object read(Object o) throws Exception {
            return myPropertyDescriptor.getReadMethod().invoke(o);
        }

        public Annotation[] getAnnotations() throws Exception {
            List<Annotation> result = new ArrayList<Annotation>();

            if (myPropertyDescriptor.getReadMethod() != null) {
                result.addAll(Arrays.asList(myPropertyDescriptor.getReadMethod().getAnnotations()));
            }

            if (myPropertyDescriptor.getWriteMethod() != null) {
                result.addAll(Arrays.asList(myPropertyDescriptor.getWriteMethod().getAnnotations()));
            }

            return result.toArray(new Annotation[result.size()]);
        }

        public String getName() {
            return myPropertyDescriptor.getName();
        }

        public Binding createBinding() throws Exception {
            return createBindingByAccessor(this);
        }
    }

    private interface Binding {
        Node serialize(Object o, Node context) throws Exception;
    }

    private static class BeanToTagBinding implements Binding {
        private String myTagName;
        private Binding[] myPropertyBindings;

        public BeanToTagBinding(Class<? extends Object> beanClass) throws Exception {
            myTagName = getTagName(beanClass);
            myPropertyBindings = getPropertyBindings(beanClass);
        }

        private Binding[] getPropertyBindings(Class<? extends Object> beanClass) throws Exception {
            List<Binding> bindings = new ArrayList<Binding>();
            Accessor[] accessors = getAccessors(beanClass);

            for (Accessor accessor : accessors) {
                bindings.add(accessor.createBinding());
            }

            return bindings.toArray(new Binding[bindings.size()]);

        }

        public Node serialize(Object o, Node context) throws Exception {
            Document ownerDocument = getOwnerDocument(context);
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

        private static String getTagName(Class<? extends Object> aClass) {
            Tag tag = aClass.getAnnotation(Tag.class);
            if (tag != null) return tag.name();

            return aClass.getSimpleName();
        }
    }


    private static Document getOwnerDocument(Node context) {
        if (context instanceof Document) return (Document) context;
        return context.getOwnerDocument();
    }

    private static Accessor[] getAccessors(Class<? extends Object> aClass) throws IntrospectionException {
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
    }

    private static class OptionTagBinding implements Binding {
        private Accessor accessor;
        private String myName;

        public OptionTagBinding(Accessor accessor) {
            this.accessor = accessor;
            myName = accessor.getName();
        }

        public Node serialize(Object o, Node context) throws Exception {
            Document ownerDocument = getOwnerDocument(context);
            Element targetElement = ownerDocument.createElement(OPTION);
            Object value = accessor.read(o);
            if (value == null) return context;

            targetElement.setAttribute("name", myName);

            Node node = getValueBinding(value).serialize(value, targetElement);
            if (node instanceof Text) {
                Text text = (Text) node;
                targetElement.setAttribute(VALUE, text.getWholeText());
            }
            else {
                targetElement.appendChild(node);
            }

            return targetElement;
        }
    }

    private static class TagBinding implements Binding {
        private Accessor accessor;
        private Tag tagAnnotation;

        public TagBinding(Accessor accessor, Tag tagAnnotation) {
            this.accessor = accessor;
            this.tagAnnotation = tagAnnotation;
        }

        public Node serialize(Object o, Node context) throws Exception {
            Document ownerDocument = getOwnerDocument(context);
            Object value = accessor.read(o);
            if (value == null) return context;

            Element v = ownerDocument.createElement(tagAnnotation.name());

            Binding binding = getValueBinding(value);
            Node node = binding.serialize(value, v);
            v.appendChild(node);

            return v;
        }
    }

    private static class PrimitiveValueBinding implements Binding {
        public Node serialize(Object o, Node context) throws Exception {
            Document ownerDocument = getOwnerDocument(context);
            return ownerDocument.createTextNode(String.valueOf(o));
        }
    }

    private static class CollectionBinding implements Binding {
        private static final String COLLECTION = "collection";

        public Node serialize(Object o, Node context) throws Exception {
            Collection collection = (Collection) o;
            Document ownerDocument = getOwnerDocument(context);

            Element c = ownerDocument.createElement(COLLECTION);
            for (Object e : collection) {
                Node node = getValueBinding(e).serialize(e, c);
                if (node instanceof Text) {
                    Text text = (Text) node;
                    Element option = ownerDocument.createElement(OPTION);
                    c.appendChild(option);
                    option.setAttribute(VALUE, text.getWholeText());
                }
                else {
                    c.appendChild(node);
                }
            }

            return c;
        }
    }

    private static class MapBinding implements Binding {
        private static final String MAP = "map";
        private static final String ENTRY = "entry";
        private static final String KEY = "key";

        public Node serialize(Object o, Node context) throws Exception {
            Map map = (Map) o;

            Document ownerDocument = getOwnerDocument(context);

            Element m = ownerDocument.createElement(MAP);

            for (Object k : map.keySet()) {
                Object v = map.get(k);

                Element entry = ownerDocument.createElement(ENTRY);
                m.appendChild(entry);

                Node kNode = getValueBinding(k).serialize(k, entry);
                Node vNode = getValueBinding(v).serialize(v, entry);

                if (kNode instanceof Text) {
                    Text text = (Text) kNode;
                    entry.setAttribute(KEY, text.getWholeText());
                }
                else {
                    Element key = ownerDocument.createElement(KEY);
                    entry.appendChild(key);
                    key.appendChild(kNode);
                }

                if (vNode instanceof Text) {
                    Text text = (Text) vNode;
                    entry.setAttribute(VALUE, text.getWholeText());
                }
                else {
                    Element value = ownerDocument.createElement(VALUE);
                    entry.appendChild(value);
                    value.appendChild(vNode);
                }
            }

            return m;
        }
    }
}
