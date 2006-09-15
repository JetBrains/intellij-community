package com.intellij.xml.impl.schema;

import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.SchemaReferencesProvider;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.impl.XmlLangAttributeDescriptor;
import com.intellij.xml.util.XmlUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;

import java.util.*;

/**
 * @author Mike
 */
public class ComplexTypeDescriptor extends TypeDescriptor {
  private XmlNSDescriptorImpl myDocumentDescriptor;
  private XmlTag myTag;
  private XmlElementDescriptor[] myElementDescriptors = null;
  @NonNls
  public static final String PROHIBITED_ATTR_VALUE = "prohibited";
  @NonNls
  public static final String OTHER_NAMESPACE_ATTR_VALUE = "##other";
  @NonNls private static final String TRUE_ATTR_VALUE = "true";
  @NonNls private static final String REF_ATTR_NAME = "ref";
  @NonNls private static final String NAME_ATTR_NAME = "name";
  @NonNls private static final String ELEMENT_TAG_NAME = "element";
  @NonNls private static final String ATTRIBUTE_TAG_NAME = "attribute";

  public ComplexTypeDescriptor(XmlNSDescriptorImpl documentDescriptor, XmlTag tag) {
    myDocumentDescriptor = documentDescriptor;
    myTag = tag;
  }

  public XmlTag getDeclaration(){
    return myTag;
  }

  public XmlElementDescriptor[] getElements() {
    if(myElementDescriptors != null) return myElementDescriptors;
    Map<String,XmlElementDescriptor> map = new LinkedHashMap<String,XmlElementDescriptor>(5);
    collectElements(map, myTag, new HashSet<XmlTag>());
    addSubstitutionGroups(map);
    filterAbstractElements(map);
    return myElementDescriptors = map.values().toArray(
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

  public XmlAttributeDescriptor[] getAttributes() {
    List<XmlAttributeDescriptor> result = new ArrayList<XmlAttributeDescriptor>();
    collectAttributes(result, myTag, new ArrayList<XmlTag>());

    if (myDocumentDescriptor.supportsStdAttributes()) addStdAttributes(result);

    return result.toArray(new XmlAttributeDescriptor[result.size()]);
  }

  private static void addStdAttributes(List<XmlAttributeDescriptor> result) {
    result.add(new XmlLangAttributeDescriptor());
  }

  private void collectElements(Map<String,XmlElementDescriptor> result, XmlTag tag, Set<XmlTag> visited) {
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
          final String namespacePrefix = XmlUtil.findPrefixByQualifiedName(ref);
          final String namespace = "".equals(namespacePrefix) ?
                                   myDocumentDescriptor.getDefaultNamespace() :
                                   tag.getNamespaceByPrefix(namespacePrefix);

          final XmlElementDescriptor element = myDocumentDescriptor.getElementDescriptor(
            local,
            namespace,
            new HashSet<XmlNSDescriptorImpl>(),
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

        if (groupTag != null) {
          XmlTag[] tags = groupTag.getSubTags();
          for (XmlTag subTag : tags) {
            collectElements(result, subTag, visited);
          }
        }
      }
    }
    else if (XmlNSDescriptorImpl.equalsToSchemaName(tag, "restriction") ||
             XmlNSDescriptorImpl.equalsToSchemaName(tag, "extension")) {
      String base = tag.getAttributeValue("base");

      if (base != null) {
        TypeDescriptor descriptor = myDocumentDescriptor.findTypeDescriptor(
          myDocumentDescriptor.getTag(),
          base);

        if (descriptor instanceof ComplexTypeDescriptor) {
          ComplexTypeDescriptor complexTypeDescriptor = (ComplexTypeDescriptor)descriptor;
          complexTypeDescriptor.collectElements(result, complexTypeDescriptor.myTag, visited);
        }

        XmlTag[] tags = tag.getSubTags();

        for (XmlTag subTag : tags) {
          collectElements(result, subTag, visited);
        }
      }
    }
    else {
      XmlTag[] tags = tag.getSubTags();

      for (XmlTag subTag : tags) {
        collectElements(result, subTag, visited);
      }
    }
  }

  private static void addElementDescriptor(Map<String,XmlElementDescriptor> result, XmlElementDescriptor element) {
    result.remove(element.getName());
    result.put(element.getName(),element);
  }

  private void collectAttributes(List<XmlAttributeDescriptor> result, XmlTag tag, List<XmlTag> visited) {
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
          addAttributeDescriptor(result, tag);
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
            final String namespacePrefix = XmlUtil.findPrefixByQualifiedName(ref);
            final String namespace = "".equals(namespacePrefix) ?
                                     myDocumentDescriptor.getDefaultNamespace() :
                                     tag.getNamespaceByPrefix(namespacePrefix);

            final XmlAttributeDescriptorImpl attributeDescriptor = myDocumentDescriptor.getAttribute(local, namespace);
            if (attributeDescriptor != null) {
              if (use != null) {
                attributeDescriptor.myUse = use;
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
        XmlTag groupTag = myDocumentDescriptor.findAttributeGroup(ref);

        if (groupTag != null) {
          XmlTag[] tags = groupTag.getSubTags();
          for (XmlTag subTag : tags) {
            collectAttributes(result, subTag, visited);
          }
        }
      }
    }
    else if (XmlNSDescriptorImpl.equalsToSchemaName(tag, "restriction") ||
             XmlNSDescriptorImpl.equalsToSchemaName(tag, "extension")) {
      String base = tag.getAttributeValue("base");

      if (base != null) {
        TypeDescriptor descriptor = myDocumentDescriptor.findTypeDescriptor(
          myDocumentDescriptor.getTag(),
          base);

        if (descriptor instanceof ComplexTypeDescriptor) {
          ComplexTypeDescriptor complexTypeDescriptor = (ComplexTypeDescriptor)descriptor;
          complexTypeDescriptor.collectAttributes(result, complexTypeDescriptor.myTag, visited);
        }

        XmlTag[] tags = tag.getSubTags();

        for (XmlTag subTag : tags) {
          collectAttributes(result, subTag, visited);
        }
      }
    }
    else {
      XmlTag[] tags = tag.getSubTags();

      for (XmlTag subTag : tags) {
        collectAttributes(result, subTag, visited);
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

  private void addAttributeDescriptor(List<XmlAttributeDescriptor> result, XmlTag tag) {
    addAttributeDescriptor(result, myDocumentDescriptor.createAttributeDescriptor(tag));
  }

  private static void addAttributeDescriptor(List<XmlAttributeDescriptor> result, XmlAttributeDescriptor descriptor) {
    removeAttributeDescriptor(result, descriptor.getName());

    result.add(descriptor);
  }

  public boolean canContainTag(String localName, String namespace) {
    return _canContainTag(localName, namespace, myTag, new HashSet<XmlTag>(5));
  }

  private boolean _canContainTag(String localName, String namespace, XmlTag tag,Set<XmlTag> visited) {
    if (visited.contains(tag)) return false;
    visited.add(tag);

    if (XmlNSDescriptorImpl.equalsToSchemaName(tag, "any")) {
      if (OTHER_NAMESPACE_ATTR_VALUE.equals(tag.getAttributeValue("namespace"))) {
        return namespace == null || !namespace.equals(myDocumentDescriptor.getDefaultNamespace());
      }
      return true;
    }
    else if (XmlNSDescriptorImpl.equalsToSchemaName(tag, "group")) {
      String ref = tag.getAttributeValue(REF_ATTR_NAME);

      if (ref != null) {
        XmlTag groupTag = myDocumentDescriptor.findGroup(ref);
        if (groupTag != null && _canContainTag(localName, namespace, groupTag,visited)) return true;
      }
    }
    else if (XmlNSDescriptorImpl.equalsToSchemaName(tag, "restriction") ||
             XmlNSDescriptorImpl.equalsToSchemaName(tag, "extension")) {
      String base = tag.getAttributeValue("base");

      if (base != null) {
        TypeDescriptor descriptor = myDocumentDescriptor.findTypeDescriptor(
          myDocumentDescriptor.getTag(),
          base);

        if (descriptor instanceof ComplexTypeDescriptor) {
          ComplexTypeDescriptor complexTypeDescriptor = (ComplexTypeDescriptor)descriptor;
          if (complexTypeDescriptor._canContainTag(localName, namespace, complexTypeDescriptor.myTag, visited)) return true;
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

        final XmlNSDescriptor _nsDescriptor = tag.getNSDescriptor(namespace, true);
        final XmlNSDescriptorImpl nsDescriptor = _nsDescriptor instanceof XmlNSDescriptorImpl ? (XmlNSDescriptorImpl)_nsDescriptor:null;
        final XmlElementDescriptor descriptor = nsDescriptor != null ? nsDescriptor.getElementDescriptor(localName, namespace):null;
        final String name = descriptorTag.getAttributeValue(NAME_ATTR_NAME);

        if (descriptor != null &&
            name != null) {
          final String substitutionValue = ((XmlTag)descriptor.getDeclaration()).getAttributeValue("substitutionGroup");

          if (substitutionValue != null && name.equals(XmlUtil.findLocalNameByQualifiedName(substitutionValue))) {
            return true; // could be more parent-child relation complex!!!
          }
        }
    }
    }

    for (XmlTag subTag : tag.getSubTags()) {
      if (_canContainTag(localName, namespace, subTag, visited)) return true;
    }

    return false;
  }

  public boolean canContainAttribute(String attributeName, String namespace) {
    return _canContainAttribute(attributeName, namespace, myTag, new THashSet<String>());
  }

  private boolean _canContainAttribute(String name, String namespace, XmlTag tag, Set<String> visited) {
    if (XmlNSDescriptorImpl.equalsToSchemaName(tag, "anyAttribute")) {
      String ns = tag.getAttributeValue("namespace");
      if (OTHER_NAMESPACE_ATTR_VALUE.equals(ns)) {
        return !namespace.equals(myDocumentDescriptor.getDefaultNamespace());
      }
      return true;
    }
    else if (XmlNSDescriptorImpl.equalsToSchemaName(tag, "attributeGroup")) {
      String ref = tag.getAttributeValue(REF_ATTR_NAME);

      if (ref != null && !visited.contains(ref)) {
        visited.add(ref);
        XmlTag groupTag = myDocumentDescriptor.findGroup(ref);

        if (groupTag != null) {
          if (_canContainAttribute(name, namespace, groupTag,visited)) return true;
        }
      }
    }
    else if (XmlNSDescriptorImpl.equalsToSchemaName(tag, "restriction") ||
             XmlNSDescriptorImpl.equalsToSchemaName(tag, "extension")) {
      String base = tag.getAttributeValue("base");

      if (base != null && !visited.contains(base)) {
        visited.add(base);
        TypeDescriptor descriptor = myDocumentDescriptor.findTypeDescriptor(
          myDocumentDescriptor.getTag(),
          base);

        if (descriptor instanceof ComplexTypeDescriptor) {
          ComplexTypeDescriptor complexTypeDescriptor = (ComplexTypeDescriptor)descriptor;
          if (complexTypeDescriptor._canContainAttribute(name, namespace,complexTypeDescriptor.getDeclaration(), visited)) return true;
        }
      }
    }

    final XmlTag[] subTags = tag.getSubTags();
    for (XmlTag subTag : subTags) {
      if (_canContainAttribute(name, namespace, subTag, visited)) return true;
    }

    return false;
  }
}
