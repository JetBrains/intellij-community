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

import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.impl.BasicXmlAttributeDescriptor;
import com.intellij.xml.util.XmlEnumeratedValueReference;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.kohsuke.rngom.digested.DAttributePattern;
import org.xml.sax.Locator;

import javax.xml.namespace.QName;
import java.util.*;

public class RngXmlAttributeDescriptor extends BasicXmlAttributeDescriptor {
  @NonNls
  private static final QName UNKNOWN = new QName("", "#unknown");

  private static final TObjectHashingStrategy<Locator> HASHING_STRATEGY = new TObjectHashingStrategy<Locator>() {
    @Override
    public int computeHashCode(Locator o) {
      final String s = o.getSystemId();
      return o.getLineNumber() * 31 + o.getColumnNumber() * 23 + (s != null ? s.hashCode() * 11 : 0);
    }
    @Override
    public boolean equals(Locator o, Locator o1) {
      if ((o.getLineNumber() == o1.getLineNumber() && o.getColumnNumber() == o1.getColumnNumber())) {
        if (Comparing.equal(o.getSystemId(), o1.getSystemId())) {
          return true;
        }
      }
      return false;
    }
  };

  private final Map<String, String> myValues;
  private final boolean myOptional;
  private final RngElementDescriptor myElementDescriptor;
  private final THashSet<Locator> myDeclarations = new THashSet<>(HASHING_STRATEGY);
  private final QName myName;

  RngXmlAttributeDescriptor(RngElementDescriptor elementDescriptor, DAttributePattern pattern, Map<String, String> values, boolean optional) {
    this(elementDescriptor, getName(pattern), values, optional, pattern.getLocation());
  }

  private static QName getName(DAttributePattern pattern) {
    final Iterator<QName> iterator = pattern.getName().listNames().iterator();
    return iterator.hasNext() ? iterator.next() : UNKNOWN;
  }

  private RngXmlAttributeDescriptor(RngElementDescriptor elementDescriptor, QName name, Map<String, String> values, boolean optional, Locator... locations) {
    myElementDescriptor = elementDescriptor;
    myValues = values;
    myOptional = optional;
    myName = name;
    myDeclarations.addAll(Arrays.asList(locations));
  }

  public RngXmlAttributeDescriptor mergeWith(RngXmlAttributeDescriptor d) {
    final QName name = d.myName.equals(UNKNOWN) ? myName : d.myName;

    final HashMap<String, String> values = new HashMap<>(myValues);
    values.putAll(d.myValues);

    final THashSet<Locator> locations = new THashSet<>(myDeclarations, HASHING_STRATEGY);
    locations.addAll(d.myDeclarations);

    return new RngXmlAttributeDescriptor(myElementDescriptor, name, values, myOptional || d.myOptional, locations.toArray(new Locator[locations.size()]));
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
    return myValues.values().contains("ID");
  }

  @Override
  public boolean hasIdRefType() {
    return myValues.values().contains("IDREF");
  }

  @Override
  @Nullable
  public String getDefaultValue() {
    return isEnumerated() ? myValues.keySet().iterator().next() : null;
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
      } else {
        copy = myValues;
      }
      return copy.keySet().toArray(new String[copy.size()]);
    } else {
      return ArrayUtil.EMPTY_STRING_ARRAY;
    }
  }

  @Override
  public PsiElement getDeclaration() {
    final Iterator<Locator> it = myDeclarations.iterator();
    if (!it.hasNext()) return null;

    return myElementDescriptor.getDeclaration(it.next());
  }

  public Collection<PsiElement> getDeclarations() {
    return ContainerUtil.map2List(myDeclarations, locator -> myElementDescriptor.getDeclaration(locator));
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
        } else {
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
  @NonNls
  public String getName() {
    return myName.getLocalPart();
  }

  @Override
  public void init(PsiElement element) {

  }

  @Override
  public Object[] getDependences() {
    return myElementDescriptor.getDependences();
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
    return new PsiReference[] { new XmlEnumeratedValueReference(element, this) {
      @Nullable
      @Override
      public PsiElement resolve() {
        if (isTokenDatatype(getValue())) {
          return getElement();
        }
        return super.resolve();
      }
    }};
  }
}