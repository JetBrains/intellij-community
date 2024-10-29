/*
 * Copyright 2007 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.plugins.relaxNG;

import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.util.XmlStringUtil;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.intellij.plugins.relaxNG.model.descriptors.CompositeDescriptor;
import org.intellij.plugins.relaxNG.model.descriptors.RngElementDescriptor;
import org.intellij.plugins.relaxNG.model.descriptors.RngXmlAttributeDescriptor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.kohsuke.rngom.digested.DElementPattern;

import java.util.Collection;
import java.util.Set;

final class RngDocumentationProvider implements DocumentationProvider {
  private static final Logger LOG = Logger.getInstance(RngDocumentationProvider.class);

  private static final @NonNls String COMPATIBILITY_ANNOTATIONS_1_0 = "http://relaxng.org/ns/compatibility/annotations/1.0";

  @Override
  public @Nullable @Nls String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
    final XmlElement c = PsiTreeUtil.getParentOfType(originalElement, XmlTag.class, XmlAttribute.class);
    if (c != null && c.getManager() == null) {
      LOG.warn("Invalid context element passed to generateDoc()", new Throwable("<stack trace>"));
      return null;
    }
    if (c instanceof XmlTag xmlElement) {
      final XmlElementDescriptor descriptor = xmlElement.getDescriptor();
      if (descriptor instanceof CompositeDescriptor d) {
        final StringBuilder sb = new StringBuilder();
        final DElementPattern[] patterns = d.getElementPatterns();
        final Set<PsiElement> elements = new ReferenceOpenHashSet<>();
        for (DElementPattern pattern : patterns) {
          final PsiElement psiElement = d.getDeclaration(pattern.getLocation());
          if (psiElement instanceof XmlTag && elements.add(psiElement)) {
            if (sb.length() > 0) {
              sb.append("<hr>");
            }
            sb.append(getDocumentationFromTag((XmlTag)psiElement, xmlElement.getLocalName(), "Element"));
          }
        }
        return makeDocumentation(sb);
      } else if (descriptor instanceof RngElementDescriptor) {
        final PsiElement declaration = descriptor.getDeclaration();
        if (declaration instanceof XmlTag) {
          return makeDocumentation(getDocumentationFromTag((XmlTag)declaration, xmlElement.getLocalName(), "Element"));
        }
      }
    } else if (c instanceof XmlAttribute attribute) {
      final XmlAttributeDescriptor descriptor = attribute.getDescriptor();
      if (descriptor instanceof RngXmlAttributeDescriptor) {
        final StringBuilder sb = new StringBuilder();
        final Collection<PsiElement> declaration = new ReferenceOpenHashSet<>(descriptor.getDeclarations());
        for (PsiElement psiElement : declaration) {
          if (psiElement instanceof XmlTag) {
            if (sb.length() > 0) {
              sb.append("<hr>");
            }
            sb.append(getDocumentationFromTag((XmlTag)psiElement, descriptor.getName(), "Attribute"));
          }
        }
        return makeDocumentation(sb);
      }
    } else if (element instanceof XmlTag) {
      return makeDocumentation(getDocumentationFromTag((XmlTag)element, ((XmlTag)element).getLocalName(), "Element"));
    }
    return null;
  }

  private static String makeDocumentation(CharSequence sb) {
    if (sb == null) return null;
    String s = sb.toString().replaceAll("\n", "<br>"); //NON-NLS
    if (!s.startsWith("<html>")) {
      s = XmlStringUtil.wrapInHtml(s);
    }
    return s;
  }

  private static StringBuilder getDocumentationFromTag(XmlTag tag, String localName, String kind) {
    if (tag.getNamespace().equals(RelaxNgMetaDataContributor.RNG_NAMESPACE)) {
      final StringBuilder sb = new StringBuilder();
      sb.append(kind).append(": <b>").append(localName).append("</b><br>");
      final XmlTag[] docTags = tag.findSubTags("documentation", COMPATIBILITY_ANNOTATIONS_1_0);
      for (XmlTag docTag : docTags) {
        sb.append(docTag.getValue().getTrimmedText());
        sb.append("\n");
      }
      final XmlTag nextTag = PsiTreeUtil.getNextSiblingOfType(tag, XmlTag.class);
      if (nextTag != null) {
        if ("documentation".equals(nextTag.getLocalName()) && COMPATIBILITY_ANNOTATIONS_1_0.equals(nextTag.getNamespace())) {
          sb.append(nextTag.getValue().getTrimmedText());
        }
      }
      return sb;
    }
    return null;
  }

  public int hashCode() {
    return 0;   // CompositeDocumentationProvider uses a HashSet that doesn't preserve order. We want to be the first one.
  }
}