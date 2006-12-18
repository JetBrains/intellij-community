package com.intellij.util.xmlb.annotations;

import com.intellij.util.xmlb.Constants;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface AbstractCollection {
  /**
   * @return whether all collection items should be surrounded with a single tag
   */
  boolean surroundWithTag() default true;

  
  String elementTag() default Constants.OPTION;
  String elementValueAttribute() default Constants.VALUE;

  Class[] elementTypes() default {};
}
