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
 * All DOM methods that return something nontrivial, not String, Integer, Boolean, PsiClass, PsiType, or {@link com.intellij.util.xml.GenericValue}
 * parameterized with all these elements, should be annotated with this annotation. The {@link #value()} parameter should
 * specify {@link com.intellij.util.xml.Converter} class able to convert this custom type to and from {@link String}.
 * Also DOM interfaces can be annotated, which will mean that all tag value methods inside will have the specified converter. 
 *
 * @author peter
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Convert {
  /**
   * @return Converter class
   */
  Class<? extends Converter> value();

  /**
   * @return whether the corresponding XML reference to be soft. Soft references are not highlighted as errors, if unresolved.
   */
  boolean soft() default false;
}
