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
import com.intellij.pom.PomTarget;
import com.intellij.util.xml.AnnotatedElement;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomNameStrategy;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Type;
import java.util.List;

/**
 * @author peter
 */
public interface AbstractDomChildrenDescription extends AnnotatedElement, PomTarget {
  @NotNull
  List<? extends DomElement> getValues(@NotNull DomElement parent);

  @NotNull
  List<? extends DomElement> getStableValues(@NotNull DomElement parent);

  @NotNull
  Type getType();

  @NotNull
  DomNameStrategy getDomNameStrategy(@NotNull DomElement parent);

  <T> T getUserData(Key<T> key);
}
