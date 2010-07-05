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

import com.intellij.pom.PomTarget;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.EvaluatedXmlName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Set;

/**
 * @author peter
 */
public interface CustomDomChildrenDescription extends AbstractDomChildrenDescription {
  @NotNull
  TagNameDescriptor getTagNameDescriptor();

  abstract class TagNameDescriptor {
    public static final TagNameDescriptor EMPTY = new TagNameDescriptor() {
      @Override
      public Set<EvaluatedXmlName> getCompletionVariants(@NotNull DomElement parent) {
        return Collections.emptySet();
      }

      @Override
      public PomTarget findDeclaration(DomElement parent, @NotNull EvaluatedXmlName name) {
        return null;
      }

      @Override
      public PomTarget findDeclaration(@NotNull DomElement child) {
        return child.getChildDescription();
      }
    };

    public abstract Set<EvaluatedXmlName> getCompletionVariants(@NotNull DomElement parent);

    @Nullable
    public abstract PomTarget findDeclaration(DomElement parent, @NotNull EvaluatedXmlName name);

    @Nullable
    public abstract PomTarget findDeclaration(@NotNull DomElement child);
    
  }
  
}
