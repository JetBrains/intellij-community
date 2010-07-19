package org.intellij.plugins.testUtil;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/*
* Created by IntelliJ IDEA.
* User: sweinreuter
* Date: 27.03.2008
*/
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface CopyFile {
  String[] value();
  String target() default "";
}
