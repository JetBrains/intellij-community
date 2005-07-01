package com.intellij.xml.impl.schema;

import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.util.XmlUtil;
import com.intellij.xml.impl.XmlLangAttributeDescriptor;

import java.util.*;

import gnu.trove.THashSet;

/**
 * @author Mike
 */
public class ComplexTypeDescriptor extends TypeDescriptor {
  private XmlNSDescriptorImpl myDocumentDescriptor;
  private XmlTag myTag;
  private XmlElementDescriptor[] myElementDescriptors = null;

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
      for (Iterator iterator = result.values().iterator(); iterator.hasNext();) {
        XmlElementDescriptorImpl descriptor = (XmlElementDescriptorImpl)iterator.next();

        final XmlElementDescriptor[] substitutes = myDocumentDescriptor.getSubstitutes(descriptor.getName(), descriptor.getNamespace());
        boolean toContinue = false;

        for (int i = 0; i < substitutes.length; i++) {
          XmlElementDescriptor substitute = substitutes[i];
          if (result.get(substitute.getName())==null) {
            toContinue = true;
            result.put(substitute.getName(),substitute);
          }
        }

        if (toContinue) continue mainLoop;
      }

      break;
    }
  }

  private void filterAbstractElements(Map<String,XmlElementDescriptor> result) {
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

  private void addStdAttributes(List<XmlAttributeDescriptor> result) {
    result.add(new XmlLangAttributeDescriptor());
  }

  private void collectElements(Map<String,XmlElementDescriptor> result, XmlTag tag, Set<XmlTag> visited) {
    if(visited.contains(tag)) return;
    visited.add(tag);
    if (XmlNSDescriptorImpl.equalsToSchemaName(tag, "element")) {
      String nameAttr = tag.getAttributeValue("name");

      if (nameAttr != null) {
        addElementDescriptor(result, myDocumentDescriptor.createElementDescriptor(tag));
      }
      else {
        String ref = tag.getAttributeValue("ref");

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
      String ref = tag.getAttributeValue("ref");

      if (ref != null) {
        XmlTag groupTag = myDocumentDescriptor.findGroup(ref);

        if (groupTag != null) {
          XmlTag[] tags = groupTag.getSubTags();
          for (int i = 0; i < tags.length; i++) {
            XmlTag subTag = tags[i];
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
          myDocumentDescriptor.myFile.getDocument().getRootTag(),
          base);

        if (descriptor instanceof ComplexTypeDescriptor) {
          ComplexTypeDescriptor complexTypeDescriptor = (ComplexTypeDescriptor)descriptor;
          complexTypeDescriptor.collectElements(result, complexTypeDescriptor.myTag, visited);
        }

        XmlTag[] tags = tag.getSubTags();

        for (int i = 0; i < tags.length; i++) {
          XmlTag subTag = tags[i];
          collectElements(result, subTag, visited);
        }
      }
    }
    else {
      XmlTag[] tags = tag.getSubTags();

      for (int i = 0; i < tags.length; i++) {
        XmlTag subTag = tags[i];
        collectElements(result, subTag, visited);
      }
    }
  }

  private void addElementDescriptor(Map<String,XmlElementDescriptor> result, XmlElementDescriptor element) {
    result.remove(element.getName());
    result.put(element.getName(),element);
  }

  private void collectAttributes(List<XmlAttributeDescriptor> result, XmlTag tag, List<XmlTag> visited) {
    if(visited.contains(tag)) return;
    visited.add(tag);
    if (XmlNSDescriptorImpl.equalsToSchemaName(tag, "element")) {
      return;
    }
    else if (XmlNSDescriptorImpl.equalsToSchemaName(tag, "attribute")) {
      String use = tag.getAttributeValue("use");
      String name = tag.getAttributeValue("name");

      if (name != null) {
        if ("prohibited".equals(use)) {
          removeAttributeDescriptor(result, name);
        }
        else {
          addAttributeDescriptor(result, tag);
        }
      }
      else {
        String ref = tag.getAttributeValue("ref");
        if (ref != null) {
          if ("prohibited".equals(use)) {
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
      String ref = tag.getAttributeValue("ref");

      if (ref != null) {
        XmlTag groupTag = myDocumentDescriptor.findAttributeGroup(ref);

        if (groupTag != null) {
          XmlTag[] tags = groupTag.getSubTags();
          for (int i = 0; i < tags.length; i++) {
            XmlTag subTag = tags[i];
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
          myDocumentDescriptor.myFile.getDocument().getRootTag(),
          base);

        if (descriptor instanceof ComplexTypeDescriptor) {
          ComplexTypeDescriptor complexTypeDescriptor = (ComplexTypeDescriptor)descriptor;
          complexTypeDescriptor.collectAttributes(result, complexTypeDescriptor.myTag, visited);
        }

        XmlTag[] tags = tag.getSubTags();

        for (int i = 0; i < tags.length; i++) {
          XmlTag subTag = tags[i];
          collectAttributes(result, subTag, visited);
        }
      }
    }
    else {
      XmlTag[] tags = tag.getSubTags();

      for (int i = 0; i < tags.length; i++) {
        XmlTag subTag = tags[i];
        collectAttributes(result, subTag, visited);
      }
    }
  }

  private void removeAttributeDescriptor(List result, String name) {
    for (Iterator iterator = result.iterator(); iterator.hasNext();) {
      XmlAttributeDescriptorImpl attributeDescriptor = (XmlAttributeDescriptorImpl)iterator.next();

      if (attributeDescriptor.getName().equals(name)) {
        iterator.remove();
      }
    }
  }

  private void addAttributeDescriptor(List<XmlAttributeDescriptor> result, XmlTag tag) {
    addAttributeDescriptor(result, myDocumentDescriptor.createAttributeDescriptor(tag));
  }

  private void addAttributeDescriptor(List<XmlAttributeDescriptor> result, XmlAttributeDescriptor descriptor) {
    String name = descriptor.getName();

    for (Iterator iterator = result.iterator(); iterator.hasNext();) {
      XmlAttributeDescriptorImpl attributeDescriptor = (XmlAttributeDescriptorImpl)iterator.next();

      if (attributeDescriptor.getName().equals(name)) {
        iterator.remove();
      }
    }

    result.add(descriptor);
  }

  public boolean canContainTag(String localName, String namespace) {
    return _canContainTag(localName, namespace, myTag);
  }

  private boolean _canContainTag(String localName, String namespace, XmlTag tag) {
    if (XmlNSDescriptorImpl.equalsToSchemaName(tag, "any")) {
      if ("##other".equals(tag.getAttributeValue("namespace"))) {
        return namespace == null || !namespace.equals(myDocumentDescriptor.getDefaultNamespace());
      }
      return true;
    }
    else if (XmlNSDescriptorImpl.equalsToSchemaName(tag, "group")) {
      String ref = tag.getAttributeValue("ref");

      if (ref != null) {
        XmlTag groupTag = myDocumentDescriptor.findGroup(ref);
        if (groupTag != null && _canContainTag(localName, namespace, groupTag)) return true;
      }
    }
    else if (XmlNSDescriptorImpl.equalsToSchemaName(tag, "restriction") ||
      XmlNSDescriptorImpl.equalsToSchemaName(tag, "extension")) {
      String base = tag.getAttributeValue("base");

      if (base != null) {
        TypeDescriptor descriptor = myDocumentDescriptor.findTypeDescriptor(
          myDocumentDescriptor.myFile.getDocument().getRootTag(),
          base);

        if (descriptor instanceof ComplexTypeDescriptor) {
          ComplexTypeDescriptor complexTypeDescriptor = (ComplexTypeDescriptor)descriptor;
          if (complexTypeDescriptor.canContainTag(localName, namespace)) return true;
        }
      }
    }

    XmlTag[] subTags = tag.getSubTags();
    for (int i = 0; i < subTags.length; i++) {
      XmlTag subTag = subTags[i];
      if (_canContainTag(localName, namespace, subTag)) return true;
    }

    return false;
  }

  public boolean canContainAttribute(String attributeName, String namespace) {
    return _canContainAttribute(attributeName, namespace, myTag, new THashSet<String>());
  }

  private boolean _canContainAttribute(String name, String namespace, XmlTag tag, Set<String> visited) {
    if (XmlNSDescriptorImpl.equalsToSchemaName(tag, "anyAttribute")) {
      String ns = tag.getAttributeValue("namespace");
      if ("##other".equals(ns)) {
        return !namespace.equals(myDocumentDescriptor.getDefaultNamespace());
      }
      return true;
    }
    else if (XmlNSDescriptorImpl.equalsToSchemaName(tag, "attributeGroup")) {
      String ref = tag.getAttributeValue("ref");

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
          myDocumentDescriptor.myFile.getDocument().getRootTag(),
          base);

        if (descriptor instanceof ComplexTypeDescriptor) {
          ComplexTypeDescriptor complexTypeDescriptor = (ComplexTypeDescriptor)descriptor;
          if (complexTypeDescriptor._canContainAttribute(name, namespace,complexTypeDescriptor.getDeclaration(), visited)) return true;
        }
      }
    }

    XmlTag[] subTags = tag.getSubTags();
    for (int i = 0; i < subTags.length; i++) {
      XmlTag subTag = subTags[i];
      if (_canContainAttribute(name, namespace, subTag,visited)) return true;
    }

    return false;
  }
}
