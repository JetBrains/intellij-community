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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import org.intellij.plugins.relaxNG.model.descriptors.CompositeDescriptor;
import org.intellij.plugins.relaxNG.model.descriptors.RngElementDescriptor;
import org.intellij.plugins.relaxNG.model.descriptors.RngXmlAttributeDescriptor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.kohsuke.rngom.digested.DElementPattern;

import java.util.Collection;
import java.util.List;

/*
* Created by IntelliJ IDEA.
* User: sweinreuter
* Date: 19.11.2007
*/
public class RngDocumentationProvider implements DocumentationProvider {
  @NonNls
  private static final String COMPATIBILITY_ANNOTATIONS_1_0 = "http://relaxng.org/ns/compatibility/annotations/1.0";

  @Nullable
  public String generateDoc(PsiElement element, PsiElement originalElement) {
    final XmlElement c = PsiTreeUtil.getParentOfType(originalElement, XmlTag.class, XmlAttribute.class);
    if (c instanceof XmlTag) {
      final XmlTag xmlElement = (XmlTag)c;
      final XmlElementDescriptor descriptor = xmlElement.getDescriptor();
      if (descriptor instanceof CompositeDescriptor) {
        final StringBuilder sb = new StringBuilder();
        final CompositeDescriptor d = (CompositeDescriptor)descriptor;
        final DElementPattern[] patterns = d.getElementPatterns();
        for (DElementPattern pattern : patterns) {
          final PsiElement psiElement = d.getDeclaration(pattern.getLocation());
          if (psiElement instanceof XmlTag) {
            if (sb.length() > 0) {
              sb.append("<hr>");
            }
            sb.append(getDocumentationFromTag((XmlTag)psiElement, xmlElement.getLocalName(), "Element"));
          }
        }
        return makeDocumentation(sb);
      } else if (descriptor instanceof RngElementDescriptor) {
        final RngElementDescriptor d = (RngElementDescriptor)descriptor;
        final PsiElement declaration = d.getDeclaration();
        if (declaration instanceof XmlTag) {
          return makeDocumentation(getDocumentationFromTag((XmlTag)declaration, xmlElement.getLocalName(), "Element"));
        }
      }
    } else if (c instanceof XmlAttribute) {
      final XmlAttribute attribute = (XmlAttribute)c;
      final XmlAttributeDescriptor descriptor = attribute.getDescriptor();
      if (descriptor instanceof RngXmlAttributeDescriptor) {
        final RngXmlAttributeDescriptor d = (RngXmlAttributeDescriptor)descriptor;
        final StringBuilder sb = new StringBuilder();
        final Collection<PsiElement> declaration = d.getDeclarations();
        for (PsiElement psiElement : declaration) {
          if (psiElement instanceof XmlTag) {
            if (sb.length() > 0) {
              sb.append("<hr>");
            }
            sb.append(getDocumentationFromTag((XmlTag)element, d.getName(), "Attribute"));
          }
        }
      }
    } else if (element instanceof XmlTag) {
      return makeDocumentation(getDocumentationFromTag((XmlTag)element, ((XmlTag)element).getLocalName(), "Element"));
    }
    return null;
  }

  private static String makeDocumentation(StringBuilder sb) {
    if (!sb.toString().startsWith("<html>")) {
      sb.insert(0, "<html>");
      sb.append("</html>");
    }
    return sb.toString().replaceAll("\n", "<br>");
  }

  private static StringBuilder getDocumentationFromTag(XmlTag tag, String localName, String kind) {
    if (tag.getNamespace().equals(ApplicationLoader.RNG_NAMESPACE)) {
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

  @Nullable
  public PsiElement getDocumentationElementForLink(PsiManager psiManager, String link, PsiElement context) {
    return null;
  }

  @Nullable
  public PsiElement getDocumentationElementForLookupItem(PsiManager psiManager, Object object, PsiElement element) {
    return null;
  }

  @Nullable
  public String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
    return null;
  }

  public List<String> getUrlFor(PsiElement element, PsiElement originalElement) {
    return null;
  }

  public int hashCode() {
    return 0;   // CompositeDocumentationProvider uses a HashSet that doesn't preserve order. We want to be the first one.
  }
}