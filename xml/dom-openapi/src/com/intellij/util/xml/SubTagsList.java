/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.xml;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

/**
 * If you have several {@link com.intellij.util.xml.SubTagList} collections and you need to access them all at once, this
  annotation will help. "value" property will hold the array of possible subtag names. One should have different
  addition methods for each subtag name, each returning its own DomElement type, each may have an "index" parameter.
  The element will be inserted in proper place in the "merged" collection, allowing you to mix elements of different
  types (if Schema or DTD allows).
 *
 * @author peter
 */

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface SubTagsList {
  String[] value();
  String tagName() default "";
}
