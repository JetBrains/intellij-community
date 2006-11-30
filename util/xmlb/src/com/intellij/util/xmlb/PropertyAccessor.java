package com.intellij.util.xmlb;

import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class PropertyAccessor implements Accessor {
    private final PropertyDescriptor myPropertyDescriptor;


    public PropertyAccessor(PropertyDescriptor myPropertyDescriptor) {
        this.myPropertyDescriptor = myPropertyDescriptor;
    }


    public Object read(Object o) {
        try {
            return myPropertyDescriptor.getReadMethod().invoke(o);
        } catch (IllegalAccessException e) {
            throw new XmlSerializationException(e);
        } catch (InvocationTargetException e) {
            throw new XmlSerializationException(e);
        }
    }

    public void write(Object o, Object value) {
        try {
            myPropertyDescriptor.getWriteMethod().invoke(o, XmlSerializer.convert(value, myPropertyDescriptor.getPropertyType()));
        } catch (IllegalAccessException e) {
            throw new XmlSerializationException(e);
        } catch (InvocationTargetException e) {
            throw new XmlSerializationException(e);
        }
    }

    public Annotation[] getAnnotations() {
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

    public Class<?> getValueClass() {
        return myPropertyDescriptor.getPropertyType();
    }

    public Type getGenericType() {
        return myPropertyDescriptor.getReadMethod().getGenericReturnType();
    }
}
