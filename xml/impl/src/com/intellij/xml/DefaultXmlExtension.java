package com.intellij.xml;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.xml.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Dmitry Avdeev
 */
public class DefaultXmlExtension extends XmlExtension {
  
  public boolean isAvailable(final XmlFile file) {
    return true;
  }

  public Set<String> getAvailableTagNames(@NotNull final XmlFile context) {

    final HashSet<String> set = new HashSet<String>();
    final Set<String> namespaces = XmlSchemaProvider.getAvailableNamespaces(context);
    if (namespaces != null) {
      for (String namespace : namespaces) {
        final XmlFile xmlFile = XmlUtil.findNamespace(context, namespace);
        if (xmlFile != null) {
          final XmlDocument document = xmlFile.getDocument();
          assert document != null;
          final XmlNSDescriptor nsDescriptor = (XmlNSDescriptor)document.getMetaData();
          assert nsDescriptor != null;
          final XmlElementDescriptor[] elementDescriptors = nsDescriptor.getRootElementsDescriptors(document);
          for (XmlElementDescriptor elementDescriptor : elementDescriptors) {
            final String name = elementDescriptor.getDefaultName();
            set.add(name);
          }
        }
      }
    }
    return set;
  }

  public Set<String> getNamespacesByTagName(@NotNull final String tagName, @NotNull final XmlFile context) {
    final HashSet<String> set = new HashSet<String>();
    final Set<String> namespaces = XmlSchemaProvider.getAvailableNamespaces(context);
    if (namespaces != null) {
      for (String namespace : namespaces) {
        final XmlFile xmlFile = XmlUtil.findNamespace(context, namespace);
        if (xmlFile != null) {
          final XmlDocument document = xmlFile.getDocument();
          assert document != null;
          final XmlNSDescriptor nsDescriptor = (XmlNSDescriptor)document.getMetaData();
          assert nsDescriptor != null;
          final XmlElementDescriptor[] elementDescriptors = nsDescriptor.getRootElementsDescriptors(document);
          for (XmlElementDescriptor elementDescriptor : elementDescriptors) {
            final String name = elementDescriptor.getDefaultName();
            if (name.equals(tagName)) {
              set.add(namespace);
              break;
            }
          }
        }
      }
    }
    return set;
  }

  public void insertNamespaceDeclaration(@NotNull final XmlFile file,
                                         @NotNull final Editor editor,
                                         @NotNull final Set<String> possibleNamespaces,
                                         @Nullable String nsPrefix,
                                         @Nullable final Runner<String, IncorrectOperationException> runAfter) throws IncorrectOperationException {

    final String namespace = possibleNamespaces.iterator().next();

    final Project project = file.getProject();
    final XmlDocument document = file.getDocument();
    assert document != null;
    final XmlTag rootTag = document.getRootTag();
    assert rootTag != null;
    final XmlAttribute[] attributes = rootTag.getAttributes();
    XmlAttribute anchor = null;
    for (XmlAttribute attribute : attributes) {
      final XmlAttributeDescriptor descriptor = attribute.getDescriptor();
      if (attribute.isNamespaceDeclaration() || (descriptor != null && descriptor.isRequired())) {
        anchor = attribute;
      } else {
        break;
      }
    }
    
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(project).getElementFactory();

    final String prefix = nsPrefix == null ? "x" : nsPrefix;
    @NonNls final String name = "xmlns" + (prefix.length() > 0 ? ":"+ prefix :"");
    final XmlAttribute attribute = elementFactory.createXmlAttribute(name, namespace);
    if (anchor == null) {
      rootTag.add(attribute);
    } else {
      rootTag.addAfter(attribute, anchor);
    }

    String location = null;
    if (namespace.length() > 0) {
      final XmlSchemaProvider provider = XmlSchemaProvider.getAvailableProvider(file);
      if (provider != null) {
        final Set<String> strings = provider.getLocations(namespace, file);
        if (strings != null && strings.size() > 0) {
          location = strings.iterator().next();
        }
      }
    }

    if (location != null) {
      XmlAttribute xmlAttribute = rootTag.getAttribute("xsi:schemaLocation");
      final String pair = namespace + " " + location;
      if (xmlAttribute == null) {
        xmlAttribute = elementFactory.createXmlAttribute("xsi:schemaLocation", pair);
        rootTag.add(xmlAttribute);
      } else {
        final String value = xmlAttribute.getValue();
        if (!value.contains(namespace)) {
          if (StringUtil.isEmptyOrSpaces(value)) {
            xmlAttribute.setValue(pair);
          } else {
            xmlAttribute.setValue(value.trim() + " " + pair);
          }
        }
      }
    }
    CodeStyleManager.getInstance(project).reformat(rootTag);
    
    if (namespace.length() == 0) {
      final XmlAttribute xmlAttribute = rootTag.getAttribute(name);
      if (xmlAttribute != null) {
        final XmlAttributeValue value = xmlAttribute.getValueElement();
        assert value != null;
        final int startOffset = value.getTextOffset();
        editor.getCaretModel().moveToOffset(startOffset);        
      }
    }
    if (runAfter != null) {
      runAfter.run(prefix);
    }
  }
}
