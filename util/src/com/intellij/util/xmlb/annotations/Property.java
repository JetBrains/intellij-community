package com.intellij.util.xmlb.annotations;

import com.intellij.util.xmlb.DefaultSerializationFilter;
import com.intellij.util.xmlb.SerializationFilter;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author mike
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.TYPE, ElementType.METHOD})
public @interface Property {
  boolean surroundWithTag() default true;
  Class<? extends SerializationFilter> filter() default DefaultSerializationFilter.class;
}
