package com.intellij.util.xmlb.annotations;

import com.intellij.util.xmlb.Constants;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.TYPE, ElementType.METHOD})
public @interface MapAnnotation  {
  boolean surroundWithTag() default true;

  String keyAttributeName() default Constants.KEY;
  String valueAttributeName() default Constants.VALUE;
  String entryTagName() default Constants.ENTRY;

  boolean surroundKeyWithTag() default true;
  boolean surroundValueWithTag() default true;
}
