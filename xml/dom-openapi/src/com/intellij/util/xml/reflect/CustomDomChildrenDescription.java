// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.util.xml.reflect;

import com.intellij.pom.PomTarget;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.EvaluatedXmlName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.Set;

public interface CustomDomChildrenDescription extends AbstractDomChildrenDescription {
  @Nullable
  TagNameDescriptor getTagNameDescriptor();

  @Nullable
  AttributeDescriptor getCustomAttributeDescriptor();

  class TagNameDescriptor {

    public Set<EvaluatedXmlName> getCompletionVariants(@NotNull DomElement parent) {
      return Collections.emptySet();
    }

    public @Nullable PomTarget findDeclaration(DomElement parent, @NotNull EvaluatedXmlName name) {
      return null;
    }

    public @Nullable PomTarget findDeclaration(@NotNull DomElement child) {
      return child.getChildDescription();
    }
    
  }

  class AttributeDescriptor extends TagNameDescriptor {
    public static final AttributeDescriptor EMPTY = new AttributeDescriptor();

    public Type getElementType(DomElement child) {
      throw new UnsupportedOperationException();
    }
  }
}
