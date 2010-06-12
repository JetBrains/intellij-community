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
package com.intellij.patterns;

import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.PairProcessor;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public abstract class XmlNamedElementPattern<T extends XmlElement & PsiNamedElement,Self extends XmlNamedElementPattern<T,Self>> extends XmlElementPattern<T,Self>{

  public XmlNamedElementPattern(@NotNull final InitialPatternCondition<T> condition) {
    super(condition);
  }

  protected abstract String getLocalName(T t);
  protected abstract String getNamespace(T t);

  public Self withLocalName(@NonNls String localName) {
    return withLocalName(StandardPatterns.string().equalTo(localName));
  }

  public Self withLocalName(final ElementPattern<String> localName) {
    return with(new PsiNamePatternCondition<T>("withLocalName", localName) {
      public String getPropertyValue(@NotNull final Object o) {
        return o instanceof XmlElement ? getLocalName((T)o) : null;
      }
    });
  }

  public Self withNamespace(@NonNls final String... namespaces) {
    return withNamespace(PlatformPatterns.string().oneOf(namespaces));
  }

  public Self withNamespace(final ElementPattern<String> namespace) {
    return with(new PatternConditionPlus<T, String>("withNamespace", namespace) {
      @Override
      public boolean processValues(T t,
                                   ProcessingContext context,
                                   PairProcessor<String, ProcessingContext> processor) {
        return processor.process(getNamespace(t), context);
      }
    });
  }

  public static class XmlAttributePattern extends XmlNamedElementPattern<XmlAttribute, XmlAttributePattern> {
    protected XmlAttributePattern() {
      super(new InitialPatternCondition<XmlAttribute>(XmlAttribute.class) {
        public boolean accepts(@Nullable final Object o, final ProcessingContext context) {
          return o instanceof XmlAttribute;
        }
      });
    }

    protected String getLocalName(XmlAttribute xmlAttribute) {
      return xmlAttribute.getLocalName();
    }

    protected String getNamespace(XmlAttribute xmlAttribute) {
      return xmlAttribute.getNamespace();
    }
    
    public XmlAttributePattern withValue(final StringPattern pattern) {
      return with(new PatternConditionPlus<XmlAttribute, String>("withValue", pattern) {
        @Override
        public boolean processValues(XmlAttribute t,
                                     ProcessingContext context,
                                     PairProcessor<String, ProcessingContext> processor) {
          return processor.process(t.getValue(), context);
        }
      });
    }

  }

}
