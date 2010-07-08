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
package com.intellij.xml;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.source.xml.TagNameReference;
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
 * @author Dmitry Avdeev
 */
public class DefaultXmlExtension extends XmlExtension {
  
  public boolean isAvailable(final PsiFile file) {
    return true;
  }

  @NotNull
  public List<Pair<String,String>> getAvailableTagNames(@NotNull final XmlFile file, @NotNull final XmlTag context) {

    final Set<String> namespaces = new HashSet<String>(Arrays.asList(context.knownNamespaces()));
    final XmlSchemaProvider provider = XmlSchemaProvider.getAvailableProvider(file);
    if (provider != null) {
      namespaces.addAll(provider.getAvailableNamespaces(file, null));
    }
    final ArrayList<String> nsInfo = new ArrayList<String>();
    final String[] names = TagNameReference.getTagNameVariants(context, namespaces, nsInfo);
    final List<Pair<String, String>> set = new ArrayList<Pair<String,String>>(names.length);
    final Iterator<String> iterator = nsInfo.iterator();
    for (String name : names) {
      final int pos = name.indexOf(':');
      final String s = pos >= 0 ? name.substring(pos + 1) : name;
      set.add(Pair.create(s, iterator.next()));
    }
    return set;
  }

  @NotNull
  public Set<String> getNamespacesByTagName(@NotNull final String tagName, @NotNull final XmlFile context) {
    final XmlSchemaProvider provider = XmlSchemaProvider.getAvailableProvider(context);
    if (provider == null) {
      return Collections.emptySet();
    }
    return provider.getAvailableNamespaces(context, tagName);
  }

  public static Set<String> filterNamespaces(final Set<String> namespaces, final String tagName, final XmlFile context) {
    if (tagName == null) {
      return namespaces;
    }
    final HashSet<String> set = new HashSet<String>();
    for (String namespace : namespaces) {
      final XmlFile xmlFile = XmlUtil.findNamespace(context, namespace);
      if (xmlFile != null) {
        final XmlDocument document = xmlFile.getDocument();
        assert document != null;
        final XmlNSDescriptor nsDescriptor = (XmlNSDescriptor)document.getMetaData();
        assert nsDescriptor != null;
        final XmlElementDescriptor[] elementDescriptors = nsDescriptor.getRootElementsDescriptors(document);
        for (XmlElementDescriptor elementDescriptor : elementDescriptors) {
          if (hasTag(elementDescriptor, tagName, new HashSet<XmlElementDescriptor>())) {
            set.add(namespace);
            break;
          }
        }
      }
    }
    return set;
  }

  private static boolean hasTag(XmlElementDescriptor elementDescriptor, String tagName, Set<XmlElementDescriptor> visited) {
    final String name = elementDescriptor.getDefaultName();
    if (name.equals(tagName)) {
      return true;
    }
    for (XmlElementDescriptor descriptor : elementDescriptor.getElementsDescriptors(null)) {
      if (!visited.contains(elementDescriptor)) {
        visited.add(elementDescriptor);
        if (hasTag(descriptor, tagName, visited)) {
          return true;
        }
      }
    }
    return false;
  }

  @NotNull
  public Set<String> guessUnboundNamespaces(@NotNull final PsiElement element, @NotNull XmlFile file) {
    if (!(element instanceof XmlTag)) {
      return Collections.emptySet();
    }
    final XmlTag tag = (XmlTag)element;
    final String name = tag.getLocalName();
    final Set<String> byTagName = getNamespacesByTagName(name, file);
    if (!byTagName.isEmpty()) {
      Set<String> filtered = new HashSet<String>(byTagName);
      filtered.removeAll(Arrays.asList(tag.knownNamespaces()));
      return filtered;
    }
    final Set<String> set = guessNamespace(file, name);
    set.removeAll(Arrays.asList(tag.knownNamespaces()));

    final XmlTag parentTag = tag.getParentTag();
    ns: for (Iterator<String> i = set.iterator(); i.hasNext();) {
      final String s = i.next();
      final Collection<XmlFile> namespaces = XmlUtil.findNSFilesByURI(s, element.getProject(), ModuleUtil.findModuleForPsiElement(file));
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
          if (descriptor.getName().equals(name)) {
            continue ns;
          }
        }
      }
      i.remove();
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
    
    final XmlSchemaProvider provider = XmlSchemaProvider.getAvailableProvider(file);
    String prefix = nsPrefix;
    if (prefix == null && provider != null) {
      prefix = provider.getDefaultPrefix(namespace, file);
    }
    if (prefix == null) {
      prefix = "";
    }
    final XmlElementFactory elementFactory = XmlElementFactory.getInstance(project);

    @NonNls final String qname = "xmlns" + (prefix.length() > 0 ? ":"+ prefix :"");
    final XmlAttribute attribute = elementFactory.createXmlAttribute(qname, namespace);
    if (anchor == null) {
      rootTag.add(attribute);
    } else {
      rootTag.addAfter(attribute, anchor);
    }

    String location = null;
    if (namespace.length() > 0) {
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

  public XmlAttribute getPrefixDeclaration(final XmlTag context, String namespacePrefix) {
    @NonNls String nsDeclarationAttrName = null;
    for(XmlTag t = context; t != null; t = t.getParentTag()) {
      if (t.hasNamespaceDeclarations()) {
        if (nsDeclarationAttrName == null) nsDeclarationAttrName = namespacePrefix.length() > 0 ? "xmlns:"+namespacePrefix:"xmlns";
        XmlAttribute attribute = t.getAttribute(nsDeclarationAttrName);
        if (attribute != null) return attribute;
      }
    }
    return null;
  }

  private static Set<String> guessNamespace(final PsiFile file, String tagName) {
    final Project project = file.getProject();
    final Collection<VirtualFile> files = XmlTagNamesIndex.getFilesByTagName(tagName, project);
    final Set<String> possibleUris = new LinkedHashSet<String>(files.size());
    for (VirtualFile virtualFile : files) {
      final String namespace = XmlNamespaceIndex.getNamespace(virtualFile, project);
      if (namespace != null) {
        possibleUris.add(namespace);
      }
    }
    return possibleUris;
  }
}
