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

/**
 * @author yole
 */
public class DefaultXmlNamespaceHelper extends XmlNamespaceHelper {
  private static final Logger LOG = Logger.getInstance(DefaultXmlNamespaceHelper.class);

  @Override
  protected boolean isAvailable(PsiFile file) {
    return true;
  }

  @Override
  public void insertNamespaceDeclaration(@NotNull final XmlFile file,
                                         @Nullable final Editor editor,
                                         @NotNull final Set<String> possibleNamespaces,
                                         @Nullable String nsPrefix,
                                         @Nullable final Runner<String, IncorrectOperationException> runAfter) throws IncorrectOperationException {

    final String namespace = possibleNamespaces.iterator().next();

    final Project project = file.getProject();
    final XmlTag rootTag = file.getRootTag();
    assert rootTag != null;
    XmlAttribute anchor = getAnchor(rootTag);

    final List<XmlSchemaProvider> providers = XmlSchemaProvider.getAvailableProviders(file);
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

    @NonNls final String qname = "xmlns" + (prefix.length() > 0 ? ":"+ prefix :"");
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

    if (editor != null && namespace.length() == 0) {
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
    if (namespace.length() > 0) {
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
  @NotNull
  public Set<String> guessUnboundNamespaces(@NotNull final PsiElement element, @NotNull XmlFile file) {
    if (!(element instanceof XmlTag)) {
      return Collections.emptySet();
    }
    final XmlTag tag = (XmlTag)element;
    final String name = tag.getLocalName();
    final Set<String> byTagName = getNamespacesByTagName(name, file);
    if (!byTagName.isEmpty()) {
      Set<String> filtered = new HashSet<>(byTagName);
      filtered.removeAll(Arrays.asList(tag.knownNamespaces()));
      return filtered;
    }
    final Set<String> set = guessNamespace(file, name);
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
        final XmlElementDescriptor[] descriptors = nsDescriptor.getRootElementsDescriptors(document);
        for (XmlElementDescriptor descriptor : descriptors) {
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

  private static Set<String> guessNamespace(final PsiFile file, String tagName) {
    final Project project = file.getProject();
    final Collection<VirtualFile> files = XmlTagNamesIndex.getFilesByTagName(tagName, project);
    final Set<String> possibleUris = new LinkedHashSet<>(files.size());
    for (VirtualFile virtualFile : files) {
      final String namespace = XmlNamespaceIndex.getNamespace(virtualFile, project, file);
      if (namespace != null) {
        possibleUris.add(namespace);
      }
    }
    return possibleUris;
  }

  @Override
  @NotNull
  public Set<String> getNamespacesByTagName(@NotNull final String tagName, @NotNull final XmlFile context) {
    final List<XmlSchemaProvider> providers = XmlSchemaProvider.getAvailableProviders(context);

    HashSet<String> set = new HashSet<>();
    for (XmlSchemaProvider provider : providers) {
      set.addAll(provider.getAvailableNamespaces(context, tagName));
    }
    return set;
  }
}
