// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.impl.schema;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.FieldCache;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.resolve.XmlResolveReferenceSupport;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.xml.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlElementsGroup;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ComplexTypeDescriptor extends TypeDescriptor {
  protected final XmlNSDescriptorImpl myDocumentDescriptor;

  private static final FieldCache<XmlElementDescriptor[],ComplexTypeDescriptor,Object, XmlElement> myElementDescriptorsCache =
    new FieldCache<>() {

      @Override
      protected XmlElementDescriptor[] compute(final ComplexTypeDescriptor complexTypeDescriptor, final XmlElement context) {
        return complexTypeDescriptor.doCollectElements(context);
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
    new FieldCache<>() {
      @Override
      protected XmlAttributeDescriptor[] compute(final ComplexTypeDescriptor complexTypeDescriptor, XmlElement p) {
        return complexTypeDescriptor.doCollectAttributes();
      }

      @Override
      protected XmlAttributeDescriptor[] getValue(final ComplexTypeDescriptor complexTypeDescriptor, Object o) {
        return complexTypeDescriptor.myAttributeDescriptors;
      }

      @Override
      protected void putValue(final XmlAttributeDescriptor[] xmlAttributeDescriptors,
                              final ComplexTypeDescriptor complexTypeDescriptor, final Object p) {
        complexTypeDescriptor.myAttributeDescriptors = xmlAttributeDescriptors;
      }
    };

  private final Map<String, CachedValue<CanContainAttributeType>> myAnyAttributeCache =
    ConcurrentFactoryMap.createMap(key -> CachedValuesManager.getManager(myTag.getProject()).createCachedValue(() -> {
      Set<Object> dependencies = new HashSet<>();
      CanContainAttributeType type = _canContainAttribute(key, myTag, null, new HashSet<>(), dependencies);
      if (dependencies.isEmpty()) {
        dependencies.add(myTag.getContainingFile());
      }
      if (DumbService.isDumb(myTag.getProject())) {
        dependencies.add(DumbService.getInstance(myTag.getProject()).getModificationTracker());
      }
      return CachedValueProvider.Result.create(type, ArrayUtil.toObjectArray(dependencies));
    }, false));

  private volatile XmlElementDescriptor[] myElementDescriptors;
  private volatile XmlAttributeDescriptor[] myAttributeDescriptors;
  private static final @NonNls String PROHIBITED_ATTR_VALUE = "prohibited";
  private static final @NonNls String OTHER_NAMESPACE_ATTR_VALUE = "##other";

  private static final @NonNls String TRUE_ATTR_VALUE = "true";
  private static final @NonNls String REF_ATTR_NAME = "ref";
  private static final @NonNls String NAME_ATTR_NAME = "name";
  private static final @NonNls String ELEMENT_TAG_NAME = "element";
  private static final @NonNls String ATTRIBUTE_TAG_NAME = "attribute";
  private boolean myHasAnyInContentModel;
  private static final @NonNls String RESTRICTION_TAG_NAME = "restriction";
  private static final @NonNls String EXTENSION_TAG_NAME = "extension";
  private static final @NonNls String BASE_ATTR_NAME = "base";

  public ComplexTypeDescriptor(XmlNSDescriptorImpl documentDescriptor, XmlTag tag) {
    super(tag);
    myDocumentDescriptor = documentDescriptor;
  }

  @SuppressWarnings("ConstantConditions")
  @Override
  public @NotNull XmlTag getDeclaration() {
    return super.getDeclaration();
  }

  public @Nullable XmlElementsGroup getTopGroup() {
    return XmlElementsGroupProcessor.computeGroups(myDocumentDescriptor, myTag);
  }

  public XmlElementDescriptor[] getElements(XmlElement context) {
    return myElementDescriptorsCache.get(null, this, context);
  }

  // Read-only calculation
  private XmlElementDescriptor[] doCollectElements(XmlElement context) {
    final Map<String,XmlElementDescriptor> map = new LinkedHashMap<>(5);
    XmlNSDescriptor descriptor = null;
    if (context instanceof XmlTag) {
      descriptor = ((XmlTag)context).getNSDescriptor(myDocumentDescriptor.getDefaultNamespace(), true);
    }
    processElements(createProcessor(map, descriptor instanceof XmlNSDescriptorImpl ? (XmlNSDescriptorImpl)descriptor : myDocumentDescriptor), map);
    return map.values().toArray(XmlElementDescriptor.EMPTY_ARRAY);
  }

  protected void processElements(XmlSchemaTagsProcessor processor, Map<String, XmlElementDescriptor> map) {
    processor.startProcessing(myTag);
    addSubstitutionGroups(map, myDocumentDescriptor, new HashSet<>());
    filterAbstractElements(map);
  }

  protected XmlSchemaTagsProcessor createProcessor(final Map<String, XmlElementDescriptor> map, final XmlNSDescriptorImpl nsDescriptor) {
    return new XmlSchemaTagsProcessor(nsDescriptor) {
      @Override
      protected void tagStarted(XmlTag tag, String tagName, XmlTag context, @Nullable XmlTag ref) {
        String refName = ref == null ? null : ref.getAttributeValue(REF_ATTR_NAME);
        addElementDescriptor(tag, tagName, map, refName, myDocumentDescriptor);
      }
    };
  }

  protected void addElementDescriptor(XmlTag tag,
                                      String tagName,
                                      Map<String, XmlElementDescriptor> map,
                                      @Nullable String refName,
                                      XmlNSDescriptorImpl nsDescriptor) {
    if ("element".equals(tagName) && tag.getAttribute("name") != null) {
      XmlElementDescriptor element = nsDescriptor.createElementDescriptor(tag);
      String name = refName == null ? element.getName() : refName;
      addElementDescriptor(map, element, name);
    }
  }

  private static void addSubstitutionGroups(Map<String, XmlElementDescriptor> result,
                                            XmlNSDescriptorImpl nsDescriptor,
                                            Set<? super XmlNSDescriptorImpl> visited) {
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
      if (XmlNSDescriptorImpl.equalsToSchemaName(tag, "include") ||
          XmlNSDescriptorImpl.equalsToSchemaName(tag, "import")) {
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
  private XmlAttributeDescriptor[] doCollectAttributes() {
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
            XmlAttributeDescriptorImpl descriptor = new XmlAttributeDescriptorImpl(tag);
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
    return result.toArray(XmlAttributeDescriptor.EMPTY);
  }

  public XmlNSDescriptorImpl getNsDescriptor() {
    return myDocumentDescriptor;
  }

  protected static void addElementDescriptor(Map<String, XmlElementDescriptor> result, XmlElementDescriptor element, String name) {
    result.remove(name);
    result.put(name, element);
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

    CurrentContextInfo(XmlNSDescriptorImpl _nsDescriptor, String _ns) {
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

    if (Objects.equals(info.expectedDefaultNs, ns) && info.documentDescriptor == nsDescriptor) return info;
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

        if (descriptor instanceof ComplexTypeDescriptor complexTypeDescriptor) {
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
        XmlAttributeValue element = ref.getValueElement();
        final PsiElement psiElement;
        if (element != null) {
          psiElement = ApplicationManager.getApplication().getService(XmlResolveReferenceSupport.class).resolveSchemaTypeOrElementOrAttributeReference(element);
          if (psiElement instanceof XmlTag) descriptorTag = (XmlTag)psiElement;
        }
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
    return _canContainAttribute(namespace, myTag, qName, new HashSet<>(), null);
  }

  enum CanContainAttributeType {
    CanContainButSkip, CanContainButDoNotSkip, CanContainAny, CanNotContain
  }

  private CanContainAttributeType _canContainAttribute(String namespace,
                                                       @NotNull XmlTag tag,
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
      else if (ns == null || "##any".equals(ns)) {
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

        if (descriptor instanceof ComplexTypeDescriptor complexTypeDescriptor) {
          if (dependencies != null) {
            XmlTag declaration = complexTypeDescriptor.getDeclaration();
            dependencies.add(declaration.getContainingFile());
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

  public String getTypeName() {
    return myTag.getAttributeValue(NAME_ATTR_NAME);
  }

  @Override
  public String toString() {
    return getTypeName();
  }
}
