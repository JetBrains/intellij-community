// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.resolve.impl;

import com.intellij.javaee.ExternalResourceManager;
import com.intellij.javaee.ExternalResourceManagerEx;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceUtil;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.URLReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.Nullable;

import static com.intellij.psi.impl.source.resolve.reference.impl.providers.URLReference.TARGET_NAMESPACE_ATTR_NAME;
import static com.intellij.psi.impl.source.resolve.reference.impl.providers.URLReference.processWsdlSchemas;

public final class UrlReferenceUtil {
  public static @Nullable PsiElement resolve(URLReference urlReference) {
    urlReference.setIncorrectResourceMapped(false);
    final String canonicalText = urlReference.getCanonicalText();

    if (canonicalText.isEmpty()) {
      final XmlAttribute attr = PsiTreeUtil.getParentOfType(urlReference.getElement(), XmlAttribute.class);

      if (attr != null &&
          attr.isNamespaceDeclaration() &&
          attr.getNamespacePrefix().isEmpty() ||
          ExternalResourceManagerEx.getInstanceEx().isIgnoredResource(canonicalText)
      ) {
        // Namespaces in XML 1.0 2nd edition, Section 6.2, last paragraph
        // The attribute value in a default namespace declaration MAY be empty. This has the same effect, within the scope of the declaration,
        // of there being no default namespace
        return urlReference.getElement();
      }
      return null;
    }

    if (ExternalResourceManagerEx.getInstanceEx().isIgnoredResource(canonicalText)) return urlReference.getElement();
    final XmlTag tag = PsiTreeUtil.getParentOfType(urlReference.getElement(), XmlTag.class);
    if (tag != null && canonicalText.equals(tag.getAttributeValue(TARGET_NAMESPACE_ATTR_NAME))) return tag;

    final PsiFile containingFile = urlReference.getElement().getContainingFile();

    if (tag != null &&
        tag.getAttributeValue("schemaLocation", XmlUtil.XML_SCHEMA_INSTANCE_URI) == null
    ) {
      final PsiFile file = ExternalResourceManager.getInstance().getResourceLocation(canonicalText, containingFile, tag.getAttributeValue("version"));
      if (file != null) return file;
    }

    if (containingFile instanceof XmlFile) {
      final XmlDocument document = ((XmlFile)containingFile).getDocument();
      assert document != null;
      final XmlTag rootTag = document.getRootTag();

      if (rootTag == null) {
        return ExternalResourceManager.getInstance().getResourceLocation(canonicalText, containingFile, null);
      }
      final XmlNSDescriptor nsDescriptor = rootTag.getNSDescriptor(canonicalText, true);
      if (nsDescriptor != null) return nsDescriptor.getDescriptorFile();

      final String url = ExternalResourceManager.getInstance().getResourceLocation(canonicalText, urlReference.getElement().getProject());
      if (!url.equals(canonicalText)) {
        PsiFile file = XmlUtil.findRelativeFile(canonicalText, urlReference.getElement().getContainingFile());
        if (file == null) {
          urlReference.setIncorrectResourceMapped(true);
        }
        return file;
      }

      if (tag == rootTag && (tag.getNamespace().equals(XmlUtil.XML_SCHEMA_URI) || tag.getNamespace().equals(XmlUtil.WSDL_SCHEMA_URI))) {
        for(XmlTag t:tag.getSubTags()) {
          final String name = t.getLocalName();
          if ("import".equals(name)) {
            if (canonicalText.equals(t.getAttributeValue("namespace"))) return t;
          } else if (!"include".equals(name) && !"redefine".equals(name) && !"annotation".equals(name)) break;
        }
      }

      final PsiElement[] result = new PsiElement[1];
      processWsdlSchemas(rootTag, t -> {
        if (canonicalText.equals(t.getAttributeValue(TARGET_NAMESPACE_ATTR_NAME))) {
          result[0] = t;
          return false;
        }
        for (XmlTag anImport : t.findSubTags("import", t.getNamespace())) {
          if (canonicalText.equals(anImport.getAttributeValue("namespace"))) {
            final XmlAttribute location = anImport.getAttribute("schemaLocation");
            if (location != null) {
              result[0] = FileReferenceUtil.findFile(location.getValueElement());
            }
          }
        }
        return true;
      });

      return result[0];
    }
    return null;
  }
}
