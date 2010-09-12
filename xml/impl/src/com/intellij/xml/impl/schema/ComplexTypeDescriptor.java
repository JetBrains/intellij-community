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
package com.intellij.xml.impl.schema;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.FieldCache;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.SchemaReferencesProvider;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlElementsGroup;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.util.XmlUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Mike
 */
public class ComplexTypeDescriptor extends TypeDescriptor {
  private final XmlNSDescriptorImpl myDocumentDescriptor;
  private final XmlTag myTag;

  private static final FieldCache<XmlElementDescriptor[],ComplexTypeDescriptor,Object, XmlElement> myElementDescriptorsCache =
    new FieldCache<XmlElementDescriptor[],ComplexTypeDescriptor,Object, XmlElement>() {

    protected XmlElementDescriptor[] compute(final ComplexTypeDescriptor complexTypeDescriptor, final XmlElement p) {
      return complexTypeDescriptor.doCollectElements(p);
    }

    protected XmlElementDescriptor[] getValue(final ComplexTypeDescriptor complexTypeDescriptor, final Object p) {
      return complexTypeDescriptor.myElementDescriptors;
    }

    protected void putValue(final XmlElementDescriptor[] xmlElementDescriptors,
                            final ComplexTypeDescriptor complexTypeDescriptor, final Object p) {
      complexTypeDescriptor.myElementDescriptors = xmlElementDescriptors;
    }
  };

  private static final FieldCache<XmlAttributeDescriptor[], ComplexTypeDescriptor, Object, XmlElement> myAttributeDescriptorsCache =
    new FieldCache<XmlAttributeDescriptor[], ComplexTypeDescriptor, Object, XmlElement>() {
    protected final XmlAttributeDescriptor[] compute(final ComplexTypeDescriptor complexTypeDescriptor, XmlElement p) {
      return complexTypeDescriptor.doCollectAttributes(p);
    }

    protected final XmlAttributeDescriptor[] getValue(final ComplexTypeDescriptor complexTypeDescriptor, Object o) {
      return complexTypeDescriptor.myAttributeDescriptors;
    }

    protected final void putValue(final XmlAttributeDescriptor[] xmlAttributeDescriptors,
                            final ComplexTypeDescriptor complexTypeDescriptor, final Object p) {
      complexTypeDescriptor.myAttributeDescriptors = xmlAttributeDescriptors;
    }
  };

  private final NullableLazyValue<XmlElementsGroup> myTopGroup = new NullableLazyValue<XmlElementsGroup>() {
    @Override
    protected XmlElementsGroup compute() {
      final Stack<XmlElementsGroup> groups = new Stack<XmlElementsGroup>();
      new XmlSchemaTagsProcessor(myDocumentDescriptor, "attribute") {

        @Override
        protected void tagStarted(XmlTag tag, XmlTag context, String tagName) {

        }
      }.startProcessing(myTag);

      return groups.isEmpty() ? null : groups.get(0);
    }
  };

  private volatile XmlElementDescriptor[] myElementDescriptors = null;
  private volatile XmlAttributeDescriptor[] myAttributeDescriptors = null;
  @NonNls
  private static final String PROHIBITED_ATTR_VALUE = "prohibited";
  @NonNls
  private static final String OTHER_NAMESPACE_ATTR_VALUE = "##other";

  @NonNls private static final String TRUE_ATTR_VALUE = "true";
  @NonNls private static final String REF_ATTR_NAME = "ref";
  @NonNls private static final String NAME_ATTR_NAME = "name";
  @NonNls private static final String ELEMENT_TAG_NAME = "element";
  @NonNls private static final String ATTRIBUTE_TAG_NAME = "attribute";
  private boolean myHasAnyInContentModel;
  @NonNls private static final String RESTRICTION_TAG_NAME = "restriction";
  @NonNls private static final String EXTENSION_TAG_NAME = "extension";
  @NonNls private static final String BASE_ATTR_NAME = "base";

  public ComplexTypeDescriptor(XmlNSDescriptorImpl documentDescriptor, XmlTag tag) {
    myDocumentDescriptor = documentDescriptor;
    myTag = tag;
  }

  public XmlTag getDeclaration(){
    return myTag;
  }

  @Nullable
  public XmlElementsGroup getTopGroup() {
    return myTopGroup.getValue();
  }

  public XmlElementDescriptor[] getElements(XmlElement context) {
    return myElementDescriptorsCache.get(null, this, context);
  }

  // Read-only calculation
  private XmlElementDescriptor[] doCollectElements(@Nullable XmlElement context) {
    final Map<String,XmlElementDescriptor> map = new LinkedHashMap<String,XmlElementDescriptor>(5);
    new XmlSchemaTagsProcessor(myDocumentDescriptor) {
      @Override
      protected void tagStarted(XmlTag tag, XmlTag context, String tagName) {
        if ("element".equals(tagName) && tag.getAttribute("name") != null) {
          addElementDescriptor(map, myDocumentDescriptor.createElementDescriptor(tag));
        }
      }
    }.startProcessing(myTag);
    addSubstitutionGroups(map);
    filterAbstractElements(map);
    return map.values().toArray(
      new XmlElementDescriptor[map.values().size()]
    );
  }

  private void addSubstitutionGroups(Map<String, XmlElementDescriptor> result) {
    mainLoop: while (true) {
      for (final XmlElementDescriptor xmlElementDescriptor : result.values()) {
        XmlElementDescriptorImpl descriptor = (XmlElementDescriptorImpl)xmlElementDescriptor;

        final XmlElementDescriptor[] substitutes = myDocumentDescriptor.getSubstitutes(descriptor.getName(), descriptor.getNamespace());
        boolean toContinue = false;

        for (XmlElementDescriptor substitute : substitutes) {
          if (result.get(substitute.getName()) == null) {
            toContinue = true;
            result.put(substitute.getName(), substitute);
          }
        }

        if (toContinue) continue mainLoop;
      }

      break;
    }
  }

  private static void filterAbstractElements(Map<String,XmlElementDescriptor> result) {
    for (Iterator<XmlElementDescriptor> iterator = result.values().iterator(); iterator.hasNext();) {
      XmlElementDescriptorImpl descriptor = (XmlElementDescriptorImpl)iterator.next();
      if (descriptor.isAbstract()) iterator.remove();
    }
  }

  public XmlAttributeDescriptor[] getAttributes(@Nullable XmlElement context) {
    return myAttributeDescriptorsCache.get(null, this, context);
  }

  // Read-only calculation
  private XmlAttributeDescriptor[] doCollectAttributes(@Nullable final XmlElement context) {
    final List<XmlAttributeDescriptor> result = new ArrayList<XmlAttributeDescriptor>();

    XmlSchemaTagsProcessor processor = new XmlSchemaTagsProcessor(myDocumentDescriptor, "element") {
      @Override
      protected void tagStarted(XmlTag tag, XmlTag context, String tagName) {
        if (ATTRIBUTE_TAG_NAME.equals(tagName)) {

          String name = tag.getAttributeValue(NAME_ATTR_NAME);
          if (name == null) return;

          String use = null;
          if (ATTRIBUTE_TAG_NAME.equals(context.getLocalName())) { // from ref
            use = context.getAttributeValue("use");
          }
          if (use == null) use = tag.getAttributeValue("use");

          if (PROHIBITED_ATTR_VALUE.equals(use)) {
            removeAttributeDescriptor(result, name);
          }
          else {
            XmlAttributeDescriptorImpl descriptor = myDocumentDescriptor.createAttributeDescriptor(tag);
            descriptor.myUse = use;
            addAttributeDescriptor(result, descriptor);
          }
        }
      }
    };
    processor.startProcessing(myTag);
    return result.toArray(new XmlAttributeDescriptor[result.size()]);
  }

  public XmlNSDescriptorImpl getNsDescriptors() {
    return myDocumentDescriptor;
  }

  private static void addElementDescriptor(Map<String,XmlElementDescriptor> result, XmlElementDescriptor element) {
    result.remove(element.getName());
    result.put(element.getName(),element);
  }

  private static void removeAttributeDescriptor(List<XmlAttributeDescriptor> result, String name) {
    for (Iterator<XmlAttributeDescriptor> iterator = result.iterator(); iterator.hasNext();) {
      XmlAttributeDescriptor attributeDescriptor = iterator.next();

      if (attributeDescriptor.getName().equals(name)) {
        iterator.remove();
      }
    }
  }

  private static void addAttributeDescriptor(List<XmlAttributeDescriptor> result, XmlAttributeDescriptor descriptor) {
    removeAttributeDescriptor(result, descriptor.getName());

    result.add(descriptor);
  }

  public boolean canContainTag(String localName, String namespace, XmlElement context) {
    return _canContainTag(localName, namespace, myTag, context, new HashSet<XmlTag>(5),
                          new CurrentContextInfo(myDocumentDescriptor, myDocumentDescriptor.getDefaultNamespace()));
  }

  static class CurrentContextInfo {
    final XmlNSDescriptorImpl documentDescriptor;
    final String expectedDefaultNs;

    public CurrentContextInfo(XmlNSDescriptorImpl _nsDescriptor, String _ns) {
      documentDescriptor = _nsDescriptor;
      expectedDefaultNs = _ns;
    }
  }

  static CurrentContextInfo getContextInfo(CurrentContextInfo info, String ref) {
    XmlTag rootTag = info.documentDescriptor.getTag();
    XmlNSDescriptorImpl nsDescriptor = XmlNSDescriptorImpl.getNSDescriptorToSearchIn(rootTag, ref, info.documentDescriptor);
    String ns;
    
    if (nsDescriptor == info.documentDescriptor) {
      ns = rootTag.getNamespaceByPrefix(XmlUtil.findPrefixByQualifiedName(ref));
    } else {
      ns = nsDescriptor.getDefaultNamespace();
    }

    if (Comparing.equal(info.expectedDefaultNs, ns) && info.documentDescriptor == nsDescriptor) return info;
    return new CurrentContextInfo(nsDescriptor, ns);
  }

  private boolean _canContainTag(String localName, String namespace, XmlTag tag, XmlElement context, Set<XmlTag> visited,
                                 CurrentContextInfo info) {
    if (visited.contains(tag)) return false;
    visited.add(tag);

    if (XmlNSDescriptorImpl.equalsToSchemaName(tag, "any")) {
      myHasAnyInContentModel = true;
      if (OTHER_NAMESPACE_ATTR_VALUE.equals(tag.getAttributeValue("namespace"))) {
        return namespace == null || !namespace.equals(info.expectedDefaultNs);
      }
      return true;
    }
    else if (XmlNSDescriptorImpl.equalsToSchemaName(tag, "group")) {
      String ref = tag.getAttributeValue(REF_ATTR_NAME);

      if (ref != null) {
        XmlTag groupTag = info.documentDescriptor.findGroup(ref);
        if (groupTag != null) {
          if (_canContainTag(localName, namespace, groupTag, context, visited, getContextInfo(info, ref)))  return true;
        }
      }
    }
    else if (XmlNSDescriptorImpl.equalsToSchemaName(tag, RESTRICTION_TAG_NAME) ||
             XmlNSDescriptorImpl.equalsToSchemaName(tag, EXTENSION_TAG_NAME)) {
      String base = tag.getAttributeValue(BASE_ATTR_NAME);

      if (base != null) {
        TypeDescriptor descriptor = info.documentDescriptor.findTypeDescriptor(base);

        if (descriptor instanceof ComplexTypeDescriptor) {
          ComplexTypeDescriptor complexTypeDescriptor = (ComplexTypeDescriptor)descriptor;
          if (complexTypeDescriptor._canContainTag(localName, namespace, complexTypeDescriptor.myTag, context, visited,
                                                   getContextInfo(info, base))) {
            myHasAnyInContentModel |= complexTypeDescriptor.myHasAnyInContentModel;
            return true;
          }
        }
      }
    } else if (XmlNSDescriptorImpl.equalsToSchemaName(tag, ELEMENT_TAG_NAME)) {
      final XmlAttribute ref = tag.getAttribute(REF_ATTR_NAME);
      XmlTag descriptorTag = tag;

      if (ref != null) {
        final PsiElement psiElement = SchemaReferencesProvider.createTypeOrElementOrAttributeReference(ref.getValueElement()).resolve();
        if (psiElement instanceof XmlTag) descriptorTag = (XmlTag)psiElement;
      }

      if (TRUE_ATTR_VALUE.equals(descriptorTag.getAttributeValue("abstract"))) {

        XmlNSDescriptor _nsDescriptor = tag.getNSDescriptor(namespace, true);
        if (_nsDescriptor == null && context instanceof XmlTag) {
          _nsDescriptor = ((XmlTag)context).getNSDescriptor(namespace, true);
        }
        final XmlNSDescriptorImpl nsDescriptor = _nsDescriptor instanceof XmlNSDescriptorImpl ? (XmlNSDescriptorImpl)_nsDescriptor:null;
        final XmlElementDescriptor descriptor = nsDescriptor != null ? nsDescriptor.getElementDescriptor(localName, namespace):null;
        final String name = descriptorTag.getAttributeValue(NAME_ATTR_NAME);

        if (descriptor != null && name != null) {
          final String substitutionValue = ((XmlTag)descriptor.getDeclaration()).getAttributeValue("substitutionGroup");

          if (substitutionValue != null && name.equals(XmlUtil.findLocalNameByQualifiedName(substitutionValue))) {
            return true; // could be more parent-child relation complex!!!
          }
        }
    }
    }

    for (XmlTag subTag : tag.getSubTags()) {
      if (_canContainTag(localName, namespace, subTag, context, visited, info)) return true;
    }

    return false;
  }

  public CanContainAttributeType canContainAttribute(String namespace) {
    return _canContainAttribute(namespace, myTag, new THashSet<String>());
  }
  
  enum CanContainAttributeType {
    CanContainButSkip, CanContainButDoNotSkip, CanNotContain
  }

  private CanContainAttributeType _canContainAttribute(String namespace, XmlTag tag, Set<String> visited) {
    if (XmlNSDescriptorImpl.equalsToSchemaName(tag, "anyAttribute")) {
      String ns = tag.getAttributeValue("namespace");
      CanContainAttributeType canContainAttributeType = CanContainAttributeType.CanContainButDoNotSkip;
      if ("skip".equals(tag.getAttributeValue("processContents"))) canContainAttributeType= CanContainAttributeType.CanContainButSkip;
      
      if (OTHER_NAMESPACE_ATTR_VALUE.equals(ns)) {
        return !namespace.equals(myDocumentDescriptor.getDefaultNamespace()) ? canContainAttributeType : CanContainAttributeType.CanNotContain;
      }
      return canContainAttributeType;
    }
    else if (XmlNSDescriptorImpl.equalsToSchemaName(tag, "attributeGroup")) {
      String ref = tag.getAttributeValue(REF_ATTR_NAME);

      if (ref != null && !visited.contains(ref)) {
        visited.add(ref);
        XmlTag groupTag = myDocumentDescriptor.findAttributeGroup(ref);

        if (groupTag != null) {
          final CanContainAttributeType containAttributeType = _canContainAttribute(namespace, groupTag, visited);
          if (containAttributeType != CanContainAttributeType.CanNotContain) return containAttributeType;
        }
      }
    }
    else if (XmlNSDescriptorImpl.equalsToSchemaName(tag, RESTRICTION_TAG_NAME) ||
             XmlNSDescriptorImpl.equalsToSchemaName(tag, EXTENSION_TAG_NAME)) {
      String base = tag.getAttributeValue(BASE_ATTR_NAME);

      if (base != null && !visited.contains(base)) {
        visited.add(base);
        TypeDescriptor descriptor = myDocumentDescriptor.findTypeDescriptor(base);

        if (descriptor instanceof ComplexTypeDescriptor) {
          ComplexTypeDescriptor complexTypeDescriptor = (ComplexTypeDescriptor)descriptor;
          final CanContainAttributeType containAttributeType =
            complexTypeDescriptor._canContainAttribute(namespace, complexTypeDescriptor.getDeclaration(), visited);
          if (containAttributeType != CanContainAttributeType.CanNotContain) return containAttributeType;
        }
      }
    }

    final XmlTag[] subTags = tag.getSubTags();
    for (XmlTag subTag : subTags) {
      final CanContainAttributeType containAttributeType = _canContainAttribute(namespace, subTag, visited);
      if (containAttributeType != CanContainAttributeType.CanNotContain) return containAttributeType;
    }

    return CanContainAttributeType.CanNotContain;
  }

  public boolean hasAnyInContentModel() {
    return myHasAnyInContentModel;
  }
}
