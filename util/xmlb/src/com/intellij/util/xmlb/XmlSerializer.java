package com.intellij.util.xmlb;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * @author mike
 */
public class XmlSerializer {
    private static final String OPTION = "option";

    public static Element serialize(Object object, Document document) throws XmlSerializationException {
        try {
            Element element = document.createElement(getTagName(object));

            Class<? extends Object> aClass = object.getClass();
            serializeProperties(element, aClass, object);

            return element;
        } catch (Exception e) {
            throw new XmlSerializationException(e);
        }
    }

    private static void serializeProperties(Element element, Class<? extends Object> aClass, Object object) throws IllegalAccessException {
        Field[] fields = aClass.getFields();
        for (Field field : fields) {
            if (Modifier.isPublic(field.getModifiers())) {
                Element option = element.getOwnerDocument().createElement(OPTION);

                option.setAttribute("name", field.getName());
                option.setAttribute("value", String.valueOf(field.get(object)));


                element.appendChild(option);
            }
        }
    }

    private static String getTagName(Object object) {
        Class<? extends Object> aClass = object.getClass();

        Tag tag = aClass.getAnnotation(Tag.class);
        if (tag != null) return tag.name();

        return aClass.getSimpleName();
    }

    public static class XmlSerializationException extends Exception {

        public XmlSerializationException() {
        }

        public XmlSerializationException(String message) {
            super(message);
        }

        public XmlSerializationException(String message, Throwable cause) {
            super(message, cause);
        }

        public XmlSerializationException(Throwable cause) {
            super(cause);
        }
    }
}
