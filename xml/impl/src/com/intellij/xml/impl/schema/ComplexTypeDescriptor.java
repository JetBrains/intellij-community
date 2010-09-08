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
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.SchemaReferencesProvider;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.util.XmlUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
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

  public XmlElementDescriptor[] getElements(XmlElement context) {
    return myElementDescriptorsCache.get(null, this, context);
  }

  // Read-only calculation
  private XmlElementDescriptor[] doCollectElements(@Nullable XmlElement context) {
    final Map<String,XmlElementDescriptor> map = new LinkedHashMap<String,XmlElementDescriptor>(5);
    new XmlSchemaTagsProcessor(myDocumentDescriptor) {
      @Override
      protected void tagStarted(XmlTag tag, String tagName) {
        if ("element".equals(tagName) && tag.getAttribute("name") != null) {
          addElementDescriptor(map, myDocumentDescriptor.createElementDescriptor(tag));
        }
      }
    }.processTag(myTag);
    //HashMap<String, XmlElementDescriptor> result = new HashMap<String, XmlElementDescriptor>();
    //collectElements(result, myTag, new THashSet<XmlTag>(), "", context);
    //if (result.size() != map.size()) {
    //  result.clear();
    //  collectElements(result, myTag, new THashSet<XmlTag>(), "", context);
    //}
    addSubstitutionGroups(map);
    filterAbstractElements(map);
    return map.values().toArray(
      new XmlElementDescriptor[map.values().size()]
    );
  }


  private void collectElements(Map<String,XmlElementDescriptor> result, XmlTag tag, Set<XmlTag> visited, @NotNull String nsPrefixFromContext,
                               @Nullable XmlElement context) {
    if(visited.contains(tag)) return;
    visited.add(tag);
    if (XmlNSDescriptorImpl.equalsToSchemaName(tag, ELEMENT_TAG_NAME)) {
      String nameAttr = tag.getAttributeValue(NAME_ATTR_NAME);

      if (nameAttr != null) {
        addElementDescriptor(result, myDocumentDescriptor.createElementDescriptor(tag));
      }
      else {
        String ref = tag.getAttributeValue(REF_ATTR_NAME);

        if (ref != null) {
          final String local = XmlUtil.findLocalNameByQualifiedName(ref);
          String namespace = getNamespace(tag, nsPrefixFromContext, ref);

          XmlNSDescriptorImpl nsDescriptor = myDocumentDescriptor;
          if (!namespace.equals(myDocumentDescriptor.getDefaultNamespace())) {
            final XmlNSDescriptor namespaceDescriptor = tag.getNSDescriptor(namespace, true);
            if (namespaceDescriptor instanceof XmlNSDescriptorImpl) nsDescriptor = (XmlNSDescriptorImpl)namespaceDescriptor;
          }

          final XmlElementDescriptor element = nsDescriptor.getElementDescriptor(
            local,
            namespace,
            new THashSet<XmlNSDescriptorImpl>(),
            true
          );

          if (element != null) {
            addElementDescriptor(result, element);
          }
        }
      }
    }
    else if (XmlNSDescriptorImpl.equalsToSchemaName(tag, "group")) {
      String ref = tag.getAttributeValue(REF_ATTR_NAME);

      if (ref != null) {
        XmlTag groupTag = myDocumentDescriptor.findGroup(ref);

        // TODO: This is bad hack, but we need context for "include-style" schemas
        if (groupTag == null && context instanceof XmlTag) {
          final XmlNSDescriptor descriptor = ((XmlTag)context).getNSDescriptor(getNamespace(tag, nsPrefixFromContext, ref), true);

          if (descriptor instanceof XmlNSDescriptorImpl && descriptor != myDocumentDescriptor) {
            groupTag = ((XmlNSDescriptorImpl)descriptor).findGroup(ref);
          }
        }

        if (groupTag != null) {
          XmlTag[] tags = groupTag.getSubTags();
          String nsPrefixFromRef = XmlUtil.findPrefixByQualifiedName(ref);
          if (nsPrefixFromRef.length() == 0) nsPrefixFromRef = nsPrefixFromContext;

          for (XmlTag subTag : tags) {
            collectElements(result, subTag, visited, nsPrefixFromRef, context);
          }
        }
      }
    }
    else if (XmlNSDescriptorImpl.equalsToSchemaName(tag, RESTRICTION_TAG_NAME) ||
             XmlNSDescriptorImpl.equalsToSchemaName(tag, EXTENSION_TAG_NAME)) {
      String base = tag.getAttributeValue(BASE_ATTR_NAME);

      if (base != null) {
        TypeDescriptor descriptor = myDocumentDescriptor.findTypeDescriptor(base);
        if (descriptor == this) {
          // TODO: similar code with SchemaReferencesProvider implementation
          final XmlNSDescriptorImpl nsDescriptor = SchemaReferencesProvider.findRedefinedDescriptor(tag, base);

          if (nsDescriptor != null) {
            descriptor = nsDescriptor.findTypeDescriptor(base);
          }
        }

        if (descriptor instanceof ComplexTypeDescriptor) {
          ComplexTypeDescriptor complexTypeDescriptor = (ComplexTypeDescriptor)descriptor;
          complexTypeDescriptor.collectElements(result, complexTypeDescriptor.myTag, visited, base,context);
        }

        XmlTag[] tags = tag.getSubTags();

        for (XmlTag subTag : tags) {
          collectElements(result, subTag, visited, nsPrefixFromContext, context);
        }
      }
    }
    else {
      XmlTag[] tags = tag.getSubTags();

      for (XmlTag subTag : tags) {
        collectElements(result, subTag, visited, nsPrefixFromContext, context);
      }
    }
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

    XmlSchemaTagsProcessor processor = new XmlSchemaTagsProcessor(myDocumentDescriptor) {
      @Override
      protected void tagStarted(XmlTag tag, String tagName) {
        if (ATTRIBUTE_TAG_NAME.equals(tagName)) {
          String use = tag.getAttributeValue("use");
          String name = tag.getAttributeValue(NAME_ATTR_NAME);

          if (name != null) {
            if (PROHIBITED_ATTR_VALUE.equals(use)) {
              removeAttributeDescriptor(result, name);
            }
            else {
              addAttributeDescriptor(result, myDocumentDescriptor.createAttributeDescriptor(tag));
            }
          }
        }
      }

      @Override
      protected void doProcessTag(XmlTag tag) {
        if (!"element".equals(tag.getLocalName())){
          super.doProcessTag(tag);
        }
      }
    };
//    processor.processTag(myTag);
    List<XmlAttributeDescriptor> descriptors = new ArrayList<XmlAttributeDescriptor>();
    collectAttributes(result, myTag, new THashSet<XmlTag>(), "",context);
    if (descriptors.size() != result.size()) {
      descriptors.clear();
//      collectAttributes(descriptors, myTag, new THashSet<XmlTag>(), "",context);

    }
    return result.toArray(new XmlAttributeDescriptor[result.size()]);
  }

  public XmlNSDescriptorImpl getNsDescriptors() {
    return myDocumentDescriptor;
  }

  private String getNamespace(final XmlTag tag, final String nsPrefixFromContext, final String ref) {
    String namespacePrefix = XmlUtil.findPrefixByQualifiedName(ref);

    String namespace = "".equals(namespacePrefix) ? myDocumentDescriptor.getDefaultNamespace() : tag.getNamespaceByPrefix(namespacePrefix);
    if (namespacePrefix.length() == 0 && nsPrefixFromContext.length() != 0) {
      String namespaceFromContext =
        ((XmlDocument)myDocumentDescriptor.getDeclaration()).getRootTag().getNamespaceByPrefix(nsPrefixFromContext);
      if (namespaceFromContext.length() > 0) namespace = namespaceFromContext;
    }
    return namespace;
  }

  private static void addElementDescriptor(Map<String,XmlElementDescriptor> result, XmlElementDescriptor element) {
    result.remove(element.getName());
    result.put(element.getName(),element);
  }

  private void collectAttributes(List<XmlAttributeDescriptor> result, XmlTag tag, THashSet<XmlTag> visited, @NotNull String nsPrefixFromContext,
                                 @Nullable XmlElement context) {
    if(visited.contains(tag)) return;
    visited.add(tag);
    if (XmlNSDescriptorImpl.equalsToSchemaName(tag, ELEMENT_TAG_NAME)) {
      return;
    }
    else if (XmlNSDescriptorImpl.equalsToSchemaName(tag, ATTRIBUTE_TAG_NAME)) {
      String use = tag.getAttributeValue("use");
      String name = tag.getAttributeValue(NAME_ATTR_NAME);

      if (name != null) {
        if (PROHIBITED_ATTR_VALUE.equals(use)) {
          removeAttributeDescriptor(result, name);
        }
        else {
          addAttributeDescriptor(result, myDocumentDescriptor.createAttributeDescriptor(tag));
        }
      }
      else {
        String ref = tag.getAttributeValue(REF_ATTR_NAME);
        if (ref != null) {
          if (PROHIBITED_ATTR_VALUE.equals(use)) {
            removeAttributeDescriptor(result, ref);
          }
          else {
            final String local = XmlUtil.findLocalNameByQualifiedName(ref);
            final String namespace = getNamespace(tag, nsPrefixFromContext, ref);

            final XmlAttributeDescriptor attributeDescriptor = myDocumentDescriptor.getAttribute(local, namespace, tag);
            if (attributeDescriptor instanceof XmlAttributeDescriptorImpl) {
              if (use != null) {
                ((XmlAttributeDescriptorImpl)attributeDescriptor).myUse = use;
              }
              addAttributeDescriptor(result, attributeDescriptor);
            }
          }
        }
      }
    }
    else if (XmlNSDescriptorImpl.equalsToSchemaName(tag, "attributeGroup")) {
      String ref = tag.getAttributeValue(REF_ATTR_NAME);

      if (ref != null) {
        XmlTag groupTag = null;
        final XmlTag parentTag = tag.getParentTag();

        // TODO: 
        if (parentTag != null && context instanceof XmlTag) {
          String parentGroupName;

          if (XmlNSDescriptorImpl.equalsToSchemaName(parentTag, "attributeGroup") &&
              (parentGroupName = parentTag.getAttributeValue("name")) != null &&
              ref.equals(parentGroupName)
             ) {
            final PsiElement element = tag.getAttribute(REF_ATTR_NAME).getValueElement().getReferences()[0].resolve();
            if (element instanceof XmlTag) groupTag = (XmlTag)element;
          } else {
            final XmlNSDescriptor descriptor = ((XmlTag)context).getNSDescriptor(getNamespace(tag, nsPrefixFromContext, ref), true);

            if (descriptor instanceof XmlNSDescriptorImpl && descriptor != myDocumentDescriptor) {
              final XmlTag group = ((XmlNSDescriptorImpl)descriptor).findAttributeGroup(ref);
              if (group != null) groupTag = group;
            }
          }
        }

        if (groupTag == null) groupTag = myDocumentDescriptor.findAttributeGroup(ref);

        if (groupTag != null) {
          XmlTag[] tags = groupTag.getSubTags();
          String nsPrefixFromRef = XmlUtil.findPrefixByQualifiedName(ref);
          if (nsPrefixFromRef.length() == 0) nsPrefixFromRef = nsPrefixFromContext;
          for (XmlTag subTag : tags) {
            collectAttributes(result, subTag, visited, nsPrefixFromRef, context);
          }
        }
      }
    }
    else if (XmlNSDescriptorImpl.equalsToSchemaName(tag, RESTRICTION_TAG_NAME) ||
             XmlNSDescriptorImpl.equalsToSchemaName(tag, EXTENSION_TAG_NAME)) {
      String base = tag.getAttributeValue(BASE_ATTR_NAME);

      if (base != null) {
        if (base.equals(myTag.getAttributeValue(NAME_ATTR_NAME))) {
          final PsiElement element = tag.getAttribute(BASE_ATTR_NAME).getValueElement().getReferences()[0].resolve();
          if (element instanceof XmlTag) {
            for (XmlTag subTag : ((XmlTag)element).getSubTags()) {
              collectAttributes(result, subTag, visited, nsPrefixFromContext, context);
            }
          }
        } else {
          TypeDescriptor descriptor = myDocumentDescriptor.findTypeDescriptor(base);

          if (descriptor instanceof ComplexTypeDescriptor) {
            ComplexTypeDescriptor complexTypeDescriptor = (ComplexTypeDescriptor)descriptor;
            complexTypeDescriptor.collectAttributes(result, complexTypeDescriptor.myTag, visited, nsPrefixFromContext, context);
          }
        }

        XmlTag[] tags = tag.getSubTags();

        for (XmlTag subTag : tags) {
          collectAttributes(result, subTag, visited, nsPrefixFromContext, context);
        }
      }
    }
    else {
      XmlTag[] tags = tag.getSubTags();

      for (XmlTag subTag : tags) {
        collectAttributes(result, subTag, visited, nsPrefixFromContext, context);
      }
    }
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
      final String ref = tag.getAttributeValue(REF_ATTR_NAME);
      XmlTag descriptorTag = tag;

      if (ref != null) {
        final PsiElement psiElement = SchemaReferencesProvider.createTypeOrElementOrAttributeReference(tag.getAttribute(REF_ATTR_NAME, null).getValueElement()).resolve();
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

  public CanContainAttributeType canContainAttribute(String attributeName, String namespace) {
    return _canContainAttribute(attributeName, namespace, myTag, new THashSet<String>());
  }
  
  enum CanContainAttributeType {
    CanContainButSkip, CanContainButDoNotSkip, CanNotContain
  }

  private CanContainAttributeType _canContainAttribute(String name, String namespace, XmlTag tag, Set<String> visited) {
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
          final CanContainAttributeType containAttributeType = _canContainAttribute(name, namespace, groupTag, visited);
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
            complexTypeDescriptor._canContainAttribute(name, namespace, complexTypeDescriptor.getDeclaration(), visited);
          if (containAttributeType != CanContainAttributeType.CanNotContain) return containAttributeType;
        }
      }
    }

    final XmlTag[] subTags = tag.getSubTags();
    for (XmlTag subTag : subTags) {
      final CanContainAttributeType containAttributeType = _canContainAttribute(name, namespace, subTag, visited);
      if (containAttributeType != CanContainAttributeType.CanNotContain) return containAttributeType;
    }

    return CanContainAttributeType.CanNotContain;
  }

  public boolean hasAnyInContentModel() {
    return myHasAnyInContentModel;
  }
}
