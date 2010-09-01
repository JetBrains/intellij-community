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

package com.intellij.util.xml.reflect;

import com.intellij.openapi.util.Key;
import com.intellij.util.xml.Converter;
import com.intellij.util.xml.DomElement;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

/**
 * @author peter
 */
public interface DomExtension {
  Key<DomElement> KEY_DECLARATION = new Key<DomElement>("DOM_DECLARATION");

  @NotNull
  Type getType();

  DomExtension setDeclaringElement(@NotNull DomElement declaringElement);
  
  DomExtension setConverter(@NotNull Converter converter);

  DomExtension setConverter(@NotNull Converter converter, boolean soft);

  DomExtension addCustomAnnotation(@NotNull Annotation anno);

  <T> void putUserData(Key<T> key, T value);

  DomExtension addExtender(final DomExtender extender);
}
