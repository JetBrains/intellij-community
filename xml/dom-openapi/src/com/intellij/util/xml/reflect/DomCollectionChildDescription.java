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

import com.intellij.ide.presentation.Presentation;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.JavaMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;

/**
 * @author peter
 */
@Presentation(typeName = "XML tag")
public interface DomCollectionChildDescription extends DomChildrenDescription {

  DomCollectionChildDescription[] EMPTY_ARRAY = new DomCollectionChildDescription[0];
  
  @Nullable
  JavaMethod getGetterMethod();

  DomElement addValue(@NotNull DomElement parent);
  DomElement addValue(@NotNull DomElement parent, int index);
  DomElement addValue(@NotNull DomElement parent, Type type);
  DomElement addValue(@NotNull DomElement parent, Type type, int index);

}
