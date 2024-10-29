/*
 * Copyright 2007 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.intellij.plugins.relaxNG.model.descriptors;

import com.intellij.lang.html.HtmlCompatibleFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.html.HtmlEnumeratedValueReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashingStrategy;
import com.intellij.xml.impl.BasicXmlAttributeDescriptor;
import com.intellij.xml.util.XmlEnumeratedValueReference;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.kohsuke.rngom.digested.DAttributePattern;
import org.xml.sax.Locator;

import javax.xml.namespace.QName;
import java.util.*;

public final class RngXmlAttributeDescriptor extends BasicXmlAttributeDescriptor {
  private static final @NonNls QName UNKNOWN = new QName("", "#unknown");

  private static final HashingStrategy<Locator> HASHING_STRATEGY = new HashingStrategy<>() {
    @Override
    public int hashCode(@Nullable Locator o) {
      if (o == null) {
        return 0;
      }

      final String s = o.getSystemId();
      return o.getLineNumber() * 31 + o.getColumnNumber() * 23 + (s != null ? s.hashCode() * 11 : 0);
    }

    @Override
    public boolean equals(@Nullable Locator o, @Nullable Locator o1) {
      if (o == o1) {
        return true;
      }
      if (o == null || o1 == null) {
        return false;
      }

      if ((o.getLineNumber() == o1.getLineNumber() && o.getColumnNumber() == o1.getColumnNumber())) {
        if (Objects.equals(o.getSystemId(), o1.getSystemId())) {
          return true;
        }
      }
      return false;
    }
  };

  private final Map<String, String> myValues;
  private final boolean myOptional;
  private final RngElementDescriptor myElementDescriptor;
  private final Set<Locator> myDeclarations = CollectionFactory.createCustomHashingStrategySet(HASHING_STRATEGY);
  private final QName myName;

  RngXmlAttributeDescriptor(RngElementDescriptor elementDescriptor,
                            DAttributePattern pattern,
                            Map<String, String> values,
                            boolean optional) {
    this(elementDescriptor, getName(pattern), values, optional, pattern.getLocation());
  }

  private static QName getName(DAttributePattern pattern) {
    final Iterator<QName> iterator = pattern.getName().listNames().iterator();
    return iterator.hasNext() ? iterator.next() : UNKNOWN;
  }

  private RngXmlAttributeDescriptor(RngElementDescriptor elementDescriptor,
                                    QName name,
                                    Map<String, String> values,
                                    boolean optional,
                                    Locator... locations) {
    myElementDescriptor = elementDescriptor;
    myValues = values;
    myOptional = optional;
    myName = name;
    myDeclarations.addAll(Arrays.asList(locations));
  }

  public RngXmlAttributeDescriptor mergeWith(RngXmlAttributeDescriptor d) {
    final QName name = d.myName.equals(UNKNOWN) ? myName : d.myName;

    Map<String, String> values = new LinkedHashMap<>(myValues);
    values.putAll(d.myValues);

    Set<Locator> locations = CollectionFactory.createCustomHashingStrategySet(HASHING_STRATEGY);
    locations.addAll(myDeclarations);
    locations.addAll(d.myDeclarations);
    return new RngXmlAttributeDescriptor(myElementDescriptor, name, values, myOptional || d.myOptional, locations.toArray(new Locator[0]));
  }

  @Override
  public boolean isRequired() {
    return !myOptional;
  }

  @Override
  public boolean isFixed() {
    return isEnumerated() && myValues.size() == 1;
  }

  @Override
  public boolean hasIdType() {
    return myValues.containsValue("ID");
  }

  @Override
  public boolean hasIdRefType() {
    return myValues.containsValue("IDREF");
  }

  @Override
  public @Nullable String getDefaultValue() {
    return isFixed() ? myValues.keySet().iterator().next() : null;
  }

  @Override
  public boolean isEnumerated() {
    return myValues.size() > 0 && myValues.get(null) == null;
  }

  @Override
  public String[] getEnumeratedValues() {
    if (myValues.size() > 0) {
      final Map<String, String> copy;
      if (myValues.get(null) != null) {
        copy = new HashMap<>(myValues);
        copy.remove(null);
      }
      else {
        copy = myValues;
      }
      return ArrayUtilRt.toStringArray(copy.keySet());
    }
    else {
      return ArrayUtilRt.EMPTY_STRING_ARRAY;
    }
  }

  @Override
  public PsiElement getDeclaration() {
    final Iterator<Locator> it = myDeclarations.iterator();
    if (!it.hasNext()) return null;

    return myElementDescriptor.getDeclaration(it.next());
  }

  @Override
  public @NotNull Collection<PsiElement> getDeclarations() {
    return ContainerUtil.map(myDeclarations, locator -> myElementDescriptor.getDeclaration(locator));
  }

  @Override
  public String getName(PsiElement context) {
    final XmlTag tag = PsiTreeUtil.getParentOfType(context, XmlTag.class, false, PsiFile.class);
    if (tag != null) {
      final String uri = myName.getNamespaceURI();
      final String prefix = tag.getPrefixByNamespace(uri);
      if (prefix != null) {
        if (prefix.length() == 0) {
          return myName.getLocalPart();
        }
        else {
          return prefix + ":" + myName.getLocalPart();
        }
      }
    }
    if (myName.getNamespaceURI().length() > 0) {
      final String prefix2 = myName.getPrefix();
      if (prefix2 != null && prefix2.length() > 0) {
        return prefix2 + ":" + myName.getLocalPart();
      }
    }
    return myName.getLocalPart();
  }

  @Override
  public @NonNls String getName() {
    return myName.getLocalPart();
  }

  @Override
  public void init(PsiElement element) {

  }

  @Override
  public Object @NotNull [] getDependencies() {
    return myElementDescriptor.getDependencies();
  }

  @Override
  public String validateValue(XmlElement context, String value) {
    if (isTokenDatatype(value)) {
      value = normalizeSpace(value);
    }
    return super.validateValue(context, value);
  }

  private boolean isTokenDatatype(String value) {
    if (myValues.containsKey(value)) {
      return "token".equals(myValues.get(value));
    }

    value = normalizeSpace(value);
    return myValues.containsKey(value) && "token".equals(myValues.get(value));
  }

  private static String normalizeSpace(String value) {
    return value.replaceAll("\\s+", " ").trim();
  }

  @Override
  public PsiReference[] getValueReferences(final XmlElement element, @NotNull String text) {
    if (element.getContainingFile() instanceof HtmlCompatibleFile) {
      return new PsiReference[]{new HtmlEnumeratedValueReference(element, this, null) {
        @Override
        public @Nullable PsiElement resolve() {
          if (isTokenDatatype(getValue())) {
            return getElement();
          }
          return super.resolve();
        }
      }};
    }
    else {
      return new PsiReference[]{new XmlEnumeratedValueReference(element, this) {
        @Override
        public @Nullable PsiElement resolve() {
          if (isTokenDatatype(getValue())) {
            return getElement();
          }
          return super.resolve();
        }
      }};
    }
  }
}
