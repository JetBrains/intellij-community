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

/**
 * A special case of {@link com.intellij.util.xml.Convert} annotation, used to convert
 * {@link com.intellij.util.xml.DomElement} instances to and from string. Converter here is an
 * instance of {@link com.intellij.util.xml.DomResolveConverter}.
 * Uses {@link com.intellij.util.xml.NameValue} annotation to retrieve the DOM element's name.
 *
 * @author peter
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface Resolve {
  /**
   * @return Class of target element. May be omitted, then the class from the annotated method return type is taken
   */
  Class<? extends DomElement> value() default DomElement.class;

  /**
   * @return whether the corresponding XML reference to be soft. Soft references are not highlighted as errors, if unresolved.
   */
  boolean soft() default false;
}
