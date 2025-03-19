// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.xml.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.index.XmlNamespaceIndex;
import com.intellij.xml.index.XmlTagNamesIndex;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;


public class DefaultXmlNamespaceHelper extends XmlNamespaceHelper {
  private static final Logger LOG = Logger.getInstance(DefaultXmlNamespaceHelper.class);

  @Override
  protected boolean isAvailable(PsiFile file) {
    return true;
  }

  @Override
  public void insertNamespaceDeclaration(final @NotNull XmlFile file,
                                         final @Nullable Editor editor,
                                         final @NotNull Set<String> possibleNamespaces,
                                         @Nullable String nsPrefix,
                                         final @Nullable Runner<String, IncorrectOperationException> runAfter) throws IncorrectOperationException {

    final String namespace = possibleNamespaces.iterator().next();

    final Project project = file.getProject();
    final XmlTag rootTag = file.getRootTag();
    assert rootTag != null;
    XmlAttribute anchor = getAnchor(rootTag);

    List<XmlSchemaProvider> providers = XmlSchemaProvider.getAvailableProviders(file);
    String prefix = getPrefix(file, nsPrefix, namespace, providers);

    final XmlElementFactory elementFactory = XmlElementFactory.getInstance(project);
    String location = getLocation(file, namespace, providers);
    String xsiPrefix = null;
    if (location != null) {
      xsiPrefix = rootTag.getPrefixByNamespace(XmlUtil.XML_SCHEMA_INSTANCE_URI);
      if (xsiPrefix == null) {
        xsiPrefix = "xsi";
        rootTag.add(elementFactory.createXmlAttribute("xmlns:xsi", XmlUtil.XML_SCHEMA_INSTANCE_URI));
      }
    }

    final @NonNls String qname = "xmlns" + (!prefix.isEmpty() ? ":" + prefix : "");
    final XmlAttribute attribute = elementFactory.createXmlAttribute(qname, namespace);
    if (anchor == null) {
      rootTag.add(attribute);
    } else {
      rootTag.addAfter(attribute, anchor);
    }

    if (location != null) {
      XmlAttribute locationAttribute = rootTag.getAttribute(XmlUtil.SCHEMA_LOCATION_ATT, XmlUtil.XML_SCHEMA_INSTANCE_URI);
      final String pair = namespace + " " + location;
      if (locationAttribute == null) {
        locationAttribute = elementFactory.createXmlAttribute(xsiPrefix + ":" + XmlUtil.SCHEMA_LOCATION_ATT, pair);
        rootTag.add(locationAttribute);
      }
      else {
        final String value = locationAttribute.getValue();
        if (!StringUtil.notNullize(value).contains(namespace)) {
          if (value == null || StringUtil.isEmptyOrSpaces(value)) {
            locationAttribute.setValue(pair);
          }
          else {
            locationAttribute.setValue(value.trim() + " " + pair);
          }
        }
      }
    }
    XmlUtil.reformatTagStart(rootTag);

    if (editor != null && namespace.isEmpty()) {
      final XmlAttribute xmlAttribute = rootTag.getAttribute(qname);
      if (xmlAttribute != null) {
        final XmlAttributeValue value = xmlAttribute.getValueElement();
        assert value != null;
        final int startOffset = value.getTextOffset();
        editor.getCaretModel().moveToOffset(startOffset);
        editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      }
    }
    if (runAfter != null) {
      runAfter.run(prefix);
    }
  }

  private static String getPrefix(XmlFile file, String nsPrefix, String namespace, List<XmlSchemaProvider> providers) {
    String prefix = nsPrefix;
    if (prefix == null) {
      for (XmlSchemaProvider provider : providers) {
        prefix = provider.getDefaultPrefix(namespace, file);
        if (prefix != null) {
          break;
        }
      }
    }
    if (prefix == null) {
      prefix = "";
    }
    return prefix;
  }

  private static XmlAttribute getAnchor(XmlTag rootTag) {
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
    return anchor;
  }

  private static String getLocation(XmlFile file, String namespace, List<XmlSchemaProvider> providers) {
    String location = null;
    if (!namespace.isEmpty()) {
      for (XmlSchemaProvider provider : providers) {
        Set<String> locations = provider.getLocations(namespace, file);
        if (locations != null && !locations.isEmpty()) {
          location = locations.iterator().next();
        }
      }
    }
    return location;
  }

  @Override
  public @NotNull Set<String> guessUnboundNamespaces(final @NotNull PsiElement element, @NotNull XmlFile file) {
    if (!(element instanceof XmlTag tag)) {
      return Collections.emptySet();
    }
    final String name = tag.getLocalName();
    final Set<String> byTagName = getNamespacesByTagName(name, file);
    if (!byTagName.isEmpty()) {
      Set<String> filtered = new HashSet<>(byTagName);
      filtered.removeAll(Arrays.asList(tag.knownNamespaces()));
      return filtered;
    }
    final Set<String> set = guessNamespace(file.getProject(), name);
    set.removeAll(Arrays.asList(tag.knownNamespaces()));

    final XmlTag parentTag = tag.getParentTag();
    ns: for (Iterator<String> i = set.iterator(); i.hasNext();) {
      final String s = i.next();
      final Collection<XmlFile> namespaces = XmlUtil.findNSFilesByURI(s, element.getProject(), ModuleUtilCore.findModuleForPsiElement(file));
      for (XmlFile namespace : namespaces) {
        final XmlDocument document = namespace.getDocument();
        assert document != null;
        final XmlNSDescriptor nsDescriptor = (XmlNSDescriptor)document.getMetaData();
        assert nsDescriptor != null;
        if (parentTag != null) {
          continue ns;
        }
        for (XmlElementDescriptor descriptor : nsDescriptor.getRootElementsDescriptors(document)) {
          if (descriptor == null) {
            LOG.error(nsDescriptor + " returned null element for getRootElementsDescriptors() array");
            continue;
          }
          if (descriptor.getName().equals(name)) {
            continue ns;
          }
        }
      }
      i.remove();
    }
    return set;
  }

  private static @NotNull Set<String> guessNamespace(@NotNull Project project, @NotNull String tagName) {
    Collection<VirtualFile> files = XmlTagNamesIndex.getFilesByTagName(tagName, project);
    if (files.isEmpty()) {
      return Collections.emptySet();
    }

    // XmlNamespaceIndex.getFileNamespace accesses FileBasedIndex, so, cannot process values (it is prohibited to process as part of another processing)
    Set<String> possibleUris = new LinkedHashSet<>(files.size());
    for (VirtualFile virtualFile : files) {
      String namespace = XmlNamespaceIndex.getNamespace(virtualFile, project);
      if (namespace != null) {
        possibleUris.add(namespace);
      }
    }
    return possibleUris;
  }

  @Override
  public @NotNull Set<String> getNamespacesByTagName(@NotNull String tagName, @NotNull XmlFile file) {
    Set<String> set = null;
    for (XmlSchemaProvider provider : XmlSchemaProvider.EP_NAME.getExtensionList()) {
      if (provider.isAvailable(file)) {
        if (set == null) {
          set = new HashSet<>();
        }
        set.addAll(provider.getAvailableNamespaces(file, tagName));
      }
    }
    return set == null ? Collections.emptySet() : set;
  }
}
