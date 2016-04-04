/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.serialization;

import com.intellij.util.xmlb.Accessor;
import com.intellij.util.xmlb.SerializationFilterBase;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Only accepts accessors with xmlb tags
 * @author Ilya.Kazakevich
 */
public final class AnnotationSerializationFilter extends SerializationFilterBase {
  @Override
  protected boolean accepts(@NotNull final Accessor accessor, @NotNull final Object bean, @Nullable final Object beanValue) {
    return accessor.getAnnotation(Property.class) != null
           || accessor.getAnnotation(Tag.class) != null
           || accessor.getAnnotation(Attribute.class) != null;
  }
}
