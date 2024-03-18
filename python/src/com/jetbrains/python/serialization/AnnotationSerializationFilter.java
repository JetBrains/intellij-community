// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.serialization;

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
