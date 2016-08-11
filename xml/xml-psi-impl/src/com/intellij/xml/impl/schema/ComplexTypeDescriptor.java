/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.FieldCache;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.SchemaReferencesProvider;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.xml.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.FactoryMap;
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
  protected final XmlNSDescriptorImpl myDocumentDescriptor;
  private final XmlTag myTag;

  private static final FieldCache<XmlElementDescriptor[],ComplexTypeDescriptor,Object, XmlElement> myElementDescriptorsCache =
    new FieldCache<XmlElementDescriptor[],ComplexTypeDescriptor,Object, XmlElement>() {

    @Override
    protected XmlElementDescriptor[] compute(final ComplexTypeDescriptor complexTypeDescriptor, final XmlElement p) {
      return complexTypeDescriptor.doCollectElements(p);
    }

    @Override
    protected XmlElementDescriptor[] getValue(final ComplexTypeDescriptor complexTypeDescriptor, final Object p) {
      return complexTypeDescriptor.myElementDescriptors;
    }

    @Override
    protected void putValue(final XmlElementDescriptor[] xmlElementDescriptors,
                            final ComplexTypeDescriptor complexTypeDescriptor, final Object p) {
      complexTypeDescriptor.myElementDescriptors = xmlElementDescriptors;
    }
  };

  private static final FieldCache<XmlAttributeDescriptor[], ComplexTypeDescriptor, Object, XmlElement> myAttributeDescriptorsCache =
    new FieldCache<XmlAttributeDescriptor[], ComplexTypeDescriptor, Object, XmlElement>() {
    @Override
    protected final XmlAttributeDescriptor[] compute(final ComplexTypeDescriptor complexTypeDescriptor, XmlElement p) {
      return complexTypeDescriptor.doCollectAttributes(p);
    }

    @Override
    protected final XmlAttributeDescriptor[] getValue(final ComplexTypeDescriptor complexTypeDescriptor, Object o) {
      return complexTypeDescriptor.myAttributeDescriptors;
    }

    @Override
    protected final void putValue(final XmlAttributeDescriptor[] xmlAttributeDescriptors,
                            final ComplexTypeDescriptor complexTypeDescriptor, final Object p) {
      complexTypeDescriptor.myAttributeDescriptors = xmlAttributeDescriptors;
    }
  };

  @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
  private final FactoryMap<String, CachedValue<CanContainAttributeType>> myAnyAttributeCache = new ConcurrentFactoryMap<String, CachedValue<CanContainAttributeType>>() {
    @Override
    protected CachedValue<CanContainAttributeType> create(final String key) {
      return CachedValuesManager.getManager(myTag.getProject()).createCachedValue(() -> {
        THashSet<Object> dependencies = new THashSet<>();
        CanContainAttributeType type = _canContainAttribute(key, myTag, null, new THashSet<>(), dependencies);
        if (dependencies.isEmpty()) {
          dependencies.add(myTag.getContainingFile());
        }
        if (DumbService.isDumb(myTag.getProject())) {
          dependencies.add(DumbService.getInstance(myTag.getProject()).getModificationTracker());
        }
        return CachedValueProvider.Result.create(type, ArrayUtil.toObjectArray(dependencies));
      }, false);
    }
  };

  private volatile XmlElementDescriptor[] myElementDescriptors;
  private volatile XmlAttributeDescriptor[] myAttributeDescriptors;
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
    return XmlElementsGroupProcessor.computeGroups(myDocumentDescriptor, myTag);
  }

  public XmlElementDescriptor[] getElements(XmlElement context) {
    return myElementDescriptorsCache.get(null, this, context);
  }

  // Read-only calculation
  private XmlElementDescriptor[] doCollectElements(@Nullable XmlElement context) {
    final Map<String,XmlElementDescriptor> map = new LinkedHashMap<>(5);
    createProcessor(map).startProcessing(myTag);
    addSubstitutionGroups(map, myDocumentDescriptor, new HashSet<>());
    filterAbstractElements(map);
    return map.values().toArray(
      new XmlElementDescriptor[map.values().size()]
    );
  }

  protected XmlSchemaTagsProcessor createProcessor(final Map<String, XmlElementDescriptor> map) {
    return new XmlSchemaTagsProcessor(myDocumentDescriptor) {
      @Override
      protected void tagStarted(XmlTag tag, String tagName, XmlTag context, XmlTag ref) {
        addElementDescriptor(tag, tagName, map);
      }
    };
  }

  protected void addElementDescriptor(XmlTag tag, String tagName, Map<String, XmlElementDescriptor> map) {
    if ("element".equals(tagName) && tag.getAttribute("name") != null) {
      addElementDescriptor(map, myDocumentDescriptor.createElementDescriptor(tag));
    }
  }

  private static void addSubstitutionGroups(Map<String, XmlElementDescriptor> result,
                                            XmlNSDescriptorImpl nsDescriptor,
                                            Set<XmlNSDescriptorImpl> visited) {
    mainLoop: while (true) {
      for (final XmlElementDescriptor xmlElementDescriptor : result.values()) {
        XmlElementDescriptorImpl descriptor = (XmlElementDescriptorImpl)xmlElementDescriptor;

        final XmlElementDescriptor[] substitutes = nsDescriptor.getSubstitutes(descriptor.getName(), descriptor.getNamespace());
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

    visited.add(nsDescriptor);
    for (XmlTag tag : nsDescriptor.getTag().getSubTags()) {
      if (XmlNSDescriptorImpl.equalsToSchemaName(tag, "include")) {
        XmlAttribute location = tag.getAttribute("schemaLocation");
        if (location != null) {
          XmlAttributeValue valueElement = location.getValueElement();
          if (valueElement != null) {
            PsiElement element = new FileReferenceSet(valueElement).resolve();
            if (element instanceof XmlFile) {
              XmlDocument document = ((XmlFile)element).getDocument();
              if (document != null) {
                PsiMetaData metaData = document.getMetaData();
                if (metaData instanceof XmlNSDescriptorImpl && !visited.contains(metaData)) {
                  addSubstitutionGroups(result, (XmlNSDescriptorImpl)metaData, visited);
                }
              }
            }
          }
        }
      }
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
    final List<XmlAttributeDescriptorImpl> result = new ArrayList<>();

    XmlSchemaTagsProcessor processor = new XmlSchemaTagsProcessor(myDocumentDescriptor, "element") {
      @Override
      protected void tagStarted(XmlTag tag, String tagName, XmlTag context, XmlTag ref) {
        if (ATTRIBUTE_TAG_NAME.equals(tagName)) {

          String name = tag.getAttributeValue(NAME_ATTR_NAME);
          if (name == null) return;

          String use = null;
          if (ATTRIBUTE_TAG_NAME.equals(context.getLocalName())) { // from ref
            use = context.getAttributeValue("use");
          }
          if (use == null) use = tag.getAttributeValue("use");

          if (PROHIBITED_ATTR_VALUE.equals(use)) {
            removeAttributeDescriptor(result, name, null);
          }
          else {
            XmlAttributeDescriptorImpl descriptor = myDocumentDescriptor.createAttributeDescriptor(tag);
            descriptor.myUse = use;
            if (ref != null) {
              descriptor.myReferenceName = ref.getAttributeValue(REF_ATTR_NAME);
            }
            addAttributeDescriptor(result, descriptor);
          }
        }
      }
    };
    processor.startProcessing(myTag);
    return result.toArray(new XmlAttributeDescriptor[result.size()]);
  }

  public XmlNSDescriptorImpl getNsDescriptor() {
    return myDocumentDescriptor;
  }

  protected static void addElementDescriptor(Map<String,XmlElementDescriptor> result, XmlElementDescriptor element) {
    result.remove(element.getName());
    result.put(element.getName(),element);
  }

  private static void removeAttributeDescriptor(List<XmlAttributeDescriptorImpl> result, String name, String referenceName) {
    for (Iterator<XmlAttributeDescriptorImpl> iterator = result.iterator(); iterator.hasNext();) {
      XmlAttributeDescriptorImpl descriptor = iterator.next();

      if (descriptor.getName().equals(name) && (referenceName == null || referenceName.equals(descriptor.myReferenceName))) {
        iterator.remove();
      }
    }
  }

  private static void addAttributeDescriptor(List<XmlAttributeDescriptorImpl> result, XmlAttributeDescriptorImpl descriptor) {
    removeAttributeDescriptor(result, descriptor.getName(), descriptor.myReferenceName);

    result.add(descriptor);
  }

  public boolean canContainTag(String localName, String namespace, XmlElement context) {
    return _canContainTag(localName, namespace, myTag, context, new HashSet<>(5),
                          new CurrentContextInfo(myDocumentDescriptor, myDocumentDescriptor.getDefaultNamespace()), false);
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
                                 CurrentContextInfo info, boolean restriction) {
    if (visited.contains(tag)) return false;
    visited.add(tag);

    if (XmlNSDescriptorImpl.equalsToSchemaName(tag, "any")) {
      if (!restriction) {
        myHasAnyInContentModel = true;
      }
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
          if (_canContainTag(localName, namespace, groupTag, context, visited, getContextInfo(info, ref), restriction))  return true;
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
                                                   getContextInfo(info, base), restriction || XmlNSDescriptorImpl.equalsToSchemaName(tag,
                                                                                                                      RESTRICTION_TAG_NAME))) {
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
      if (_canContainTag(localName, namespace, subTag, context, visited, info, restriction)) return true;
    }

    return false;
  }

  public CanContainAttributeType canContainAttribute(String namespace, @Nullable String qName) {
    if (qName == null) {
      return myAnyAttributeCache.get(namespace).getValue();
    }
    return _canContainAttribute(namespace, myTag, qName, new THashSet<>(), null);
  }
  
  enum CanContainAttributeType {
    CanContainButSkip, CanContainButDoNotSkip, CanContainAny, CanNotContain
  }

  private CanContainAttributeType _canContainAttribute(String namespace,
                                                       XmlTag tag,
                                                       @Nullable String qName,
                                                       Set<String> visited,
                                                       @Nullable Set<Object> dependencies) {
    if (XmlNSDescriptorImpl.equalsToSchemaName(tag, "anyAttribute")) {
      if (dependencies != null) {
        dependencies.add(tag.getContainingFile());
      }
      String ns = tag.getAttributeValue("namespace");
      CanContainAttributeType canContainAttributeType = CanContainAttributeType.CanContainButDoNotSkip;
      if ("skip".equals(tag.getAttributeValue("processContents"))) canContainAttributeType= CanContainAttributeType.CanContainButSkip;
      
      if (OTHER_NAMESPACE_ATTR_VALUE.equals(ns)) {
        return !namespace.equals(myDocumentDescriptor.getDefaultNamespace()) ? canContainAttributeType : CanContainAttributeType.CanNotContain;
      }
      else if ("##any".equals(ns)) {
        return CanContainAttributeType.CanContainAny;
      }
      return canContainAttributeType;
    }
    else if (XmlNSDescriptorImpl.equalsToSchemaName(tag, "attributeGroup")) {
      String ref = tag.getAttributeValue(REF_ATTR_NAME);

      if (ref != null && !visited.contains(ref)) {
        visited.add(ref);
        XmlTag groupTag = myDocumentDescriptor.findAttributeGroup(ref);

        if (groupTag != null) {
          if (dependencies != null) {
            dependencies.add(groupTag.getContainingFile());
          }
          final CanContainAttributeType containAttributeType = _canContainAttribute(namespace, groupTag, qName, visited, dependencies);
          if (containAttributeType != CanContainAttributeType.CanNotContain) return containAttributeType;
        }
      }
    }
    else if (XmlNSDescriptorImpl.equalsToSchemaName(tag, "attribute")) {
      if (qName != null && qName.equals(tag.getAttributeValue(REF_ATTR_NAME))) {
        return CanContainAttributeType.CanContainButDoNotSkip;
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
          if (dependencies != null) {
            dependencies.add(((ComplexTypeDescriptor)descriptor).getDeclaration().getContainingFile());
          }

          final CanContainAttributeType containAttributeType =
            complexTypeDescriptor._canContainAttribute(namespace, complexTypeDescriptor.getDeclaration(), qName, visited, dependencies);
          if (containAttributeType != CanContainAttributeType.CanNotContain) return containAttributeType;
        }
      }
    }

    final XmlTag[] subTags = tag.getSubTags();
    for (XmlTag subTag : subTags) {
      final CanContainAttributeType containAttributeType = _canContainAttribute(namespace, subTag, qName, visited, dependencies);
      if (containAttributeType != CanContainAttributeType.CanNotContain) return containAttributeType;
    }

    return CanContainAttributeType.CanNotContain;
  }

  public boolean hasAnyInContentModel() {
    return myHasAnyInContentModel;
  }

  public int getContentType() {

    if ("simpleType".equals(myTag.getLocalName()) || "true".equals(myTag.getAttributeValue("mixed"))) {
      return XmlElementDescriptor.CONTENT_TYPE_MIXED;
    }

    if (getElements(null).length > 0) return XmlElementDescriptor.CONTENT_TYPE_CHILDREN;

    for (XmlTag tag : myTag.getSubTags()) {
      if ("simpleContent".equals(tag.getLocalName())) {
        return XmlElementDescriptor.CONTENT_TYPE_MIXED;
      }
    }
    return XmlElementDescriptor.CONTENT_TYPE_EMPTY;

  }
}
