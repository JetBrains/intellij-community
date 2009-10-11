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
 * Annotate a method returning either {@link String} or {@link com.intellij.util.xml.GenericValue} with @NameValue,
 * and {@link com.intellij.util.xml.ElementPresentationManager#getElementName(Object)} will return the resulting String
 * or {@link GenericValue#getStringValue()}.
 *
 * @author peter
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface NameValue {
  /**
   * @return whether the element's name should be unique in some scope ({@link com.intellij.util.xml.DomFileDescription#getIdentityScope(DomElement)}).
   * If true, then duplicate values will be highlighted as errors.
   */
  boolean unique() default true;

  /**
   * @return Only usable if the annotated method returns {@link com.intellij.util.xml.GenericDomValue}<Something>.
   * Then this flag controls, whether a reference should be created from the @NameValue child XML element to
   * the XML element of the annotated DOM element. Such reference is useful in rename refactoring to act as
   * an element's declaration.
   */
  boolean referencable() default true;
}
