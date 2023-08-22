// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml.impl.schema;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import com.intellij.xml.index.SchemaTypeInfo;
import com.intellij.xml.index.SchemaTypeInheritanceIndex;
import com.intellij.xml.index.XmlNamespaceIndex;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.BiFunction;

public class SchemaDefinitionsSearch implements QueryExecutor<PsiElement, PsiElement> {
  @Override
  public boolean execute(@NotNull final PsiElement queryParameters, @NotNull final Processor<? super PsiElement> consumer) {
    if (queryParameters instanceof XmlTag xml) {
      if (ReadAction.compute(() -> isTypeElement(xml))) {
        final Collection<SchemaTypeInfo> infos = ReadAction.compute(() -> gatherInheritors(xml));

        if (infos != null && ! infos.isEmpty()) {
          final XmlFile file = XmlUtil.getContainingFile(xml);
          final Project project = file.getProject();
          final Module module = ModuleUtilCore.findModuleForPsiElement(queryParameters);
          //if (module == null) return false;

          final VirtualFile vf = file.getVirtualFile();
          String thisNs = ReadAction.compute(() -> XmlNamespaceIndex.getNamespace(vf, project));
          thisNs = thisNs == null ? getDefaultNs(file) : thisNs;
          // so thisNs can be null
          if (thisNs == null) return false;
          final ArrayList<SchemaTypeInfo> infosLst = new ArrayList<>(infos);
          Collections.sort(infosLst);
          final Map<String, Set<XmlFile>> nsMap = new HashMap<>();
          for (final SchemaTypeInfo info : infosLst) {
            Set<XmlFile> targetFiles = nsMap.get(info.getNamespaceUri());
            if (targetFiles == null) {
              targetFiles = new HashSet<>();
              if (Objects.equals(info.getNamespaceUri(), thisNs)) {
                targetFiles.add(file);
              }
              final Collection<XmlFile> files = ReadAction.compute(() -> XmlUtil.findNSFilesByURI(info.getNamespaceUri(), project, module));
              if (files != null) {
                targetFiles.addAll(files);
              }
              nsMap.put(info.getNamespaceUri(), targetFiles);
            }
            if (! targetFiles.isEmpty()) {
              for (final XmlFile targetFile : targetFiles) {
                ApplicationManager.getApplication().runReadAction(() -> {
                  final String prefixByURI = XmlUtil.findNamespacePrefixByURI(targetFile, info.getNamespaceUri());
                  if (prefixByURI == null) return;
                  final PsiElementProcessor processor = new PsiElementProcessor() {
                    @Override
                    public boolean execute(@NotNull PsiElement element) {
                      if (element instanceof XmlTag) {
                        if (isCertainTypeElement((XmlTag)element, info.getTagName(), prefixByURI) ||
                            isElementWithEmbeddedType((XmlTag)element, info.getTagName(), prefixByURI)) {
                          consumer.process(element);
                          return false;
                        }
                      }
                      return true;
                    }
                  };
                  XmlUtil.processXmlElements(targetFile, processor, true);
                });
              }
            }
          }
        }
      }
    }
    return true;
  }

  public static boolean isElementWithSomeEmbeddedType(XmlTag xml) {
    final String localName = xml.getLocalName();
    if (! (XmlUtil.XML_SCHEMA_URI.equals(xml.getNamespace()) && "element".equals(localName))) {
      return false;
    }
    final XmlTag[] tags = xml.getSubTags();
    for (XmlTag tag : tags) {
      if (isTypeElement(tag)) {
        return true;
      }
    }
    return false;
  }

  public static boolean isElementWithEmbeddedType(XmlTag xml, final String typeName, final String typeNsPrefix) {
    final String localName = xml.getLocalName();
    if (! (XmlUtil.XML_SCHEMA_URI.equals(xml.getNamespace()) && "element".equals(localName))) {
      return false;
    }
    final XmlAttribute nameAttr = getNameAttr(xml);
    if (nameAttr == null || nameAttr.getValue() == null) return false;
    final String localTypeName = XmlUtil.findLocalNameByQualifiedName(nameAttr.getValue());
    final String prefix = XmlUtil.findPrefixByQualifiedName(nameAttr.getValue());
    if (! typeName.equals(localTypeName) || ! typeNsPrefix.equals(prefix)) {
      return false;
    }
    final XmlTag[] tags = xml.getSubTags();
    for (XmlTag tag : tags) {
      if (isTypeElement(tag)) {
        return true;
      }
    }
    return false;
  }

  private boolean isCertainTypeElement(XmlTag xml, final String typeName, final String nsPrefix) {
    if (! isTypeElement(xml)) return false;
    final XmlAttribute name = getNameAttr(xml);
    if (name == null) return false;
    final String value = name.getValue();
    if (value == null) return false;
    final String localName = XmlUtil.findLocalNameByQualifiedName(value);
    return typeName.equals(localName) && nsPrefix.equals(XmlUtil.findPrefixByQualifiedName(value));
  }

  public static boolean isTypeElement(XmlTag xml) {
    final String localName = xml.getLocalName();
    return XmlUtil.XML_SCHEMA_URI.equals(xml.getNamespace()) && ("complexType".equals(localName) || "simpleType".equals(localName));
  }

  private Collection<SchemaTypeInfo> gatherInheritors(XmlTag xml) {
    XmlAttribute name = getNameAttr(xml);
    if (name == null || StringUtil.isEmptyOrSpaces(name.getValue())) return null;
    String localName = name.getValue();
    final boolean hasPrefix = localName.contains(":");
    localName = hasPrefix ? localName.substring(localName.indexOf(':') + 1) : localName;
    final String nsPrefix = hasPrefix ? name.getValue().substring(0, name.getValue().indexOf(':')) : null;

    final XmlFile file = XmlUtil.getContainingFile(xml);
    if (file == null) return null;
    final Project project = file.getProject();

    final Set<SchemaTypeInfo> result = new HashSet<>();
    final ArrayDeque<SchemaTypeInfo> queue = new ArrayDeque<>();

    String nsUri;
    if (! hasPrefix) {
      nsUri = getDefaultNs(file);
    } else {
      nsUri = XmlUtil.findNamespaceByPrefix(nsPrefix, file.getRootTag());
    }
    if (nsUri == null) return null;

    queue.add(new SchemaTypeInfo(localName, true, nsUri));

    final BiFunction<String, String, List<Set<SchemaTypeInfo>>> worker =
      SchemaTypeInheritanceIndex.getWorker(project, file.getContainingFile().getVirtualFile());
    while (! queue.isEmpty()) {
      final SchemaTypeInfo info = queue.removeFirst();
      final List<Set<SchemaTypeInfo>> childrenOfType = worker.apply(info.getNamespaceUri(), info.getTagName());
      for (Set<SchemaTypeInfo> infos : childrenOfType) {
        for (SchemaTypeInfo typeInfo : infos) {
          if (typeInfo.isIsTypeName()) {
            queue.add(typeInfo);
          }
          result.add(typeInfo);
        }
      }
    }
    return result;
  }

  public static XmlAttribute getNameAttr(XmlTag xml) {
    XmlAttribute name = xml.getAttribute("name", XmlUtil.XML_SCHEMA_URI);
    name = name == null ? xml.getAttribute("name") : name;
    return name;
  }

  private String getDefaultNs(final XmlFile file) {
    return ReadAction.compute(() -> {
      String nsUri;
      final XmlTag tag = file.getDocument().getRootTag();
      XmlAttribute xmlns = tag.getAttribute("xmlns", XmlUtil.XML_SCHEMA_URI);
      xmlns = xmlns == null ? tag.getAttribute("xmlns") : xmlns;
      nsUri = xmlns == null ? null : xmlns.getValue();
      return nsUri;
    });
  }
}
