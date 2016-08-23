/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.xml.util.documentation;

import com.intellij.codeInsight.completion.XmlCompletionData;
import com.intellij.lang.Language;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.lang.documentation.DocumentationUtil;
import com.intellij.lang.xhtml.XHTMLLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.impl.source.xml.SchemaPrefix;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.xml.*;
import com.intellij.xml.impl.schema.*;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author maxim
 */
public class XmlDocumentationProvider implements DocumentationProvider {
  private static final Key<XmlElementDescriptor> DESCRIPTOR_KEY = Key.create("Original element");

  private static final Logger LOG = Logger.getInstance("#com.intellij.xml.util.documentation.XmlDocumentationProvider");

  @NonNls private static final String NAME_ATTR_NAME = "name";
  @NonNls private static final String BASE_SITEPOINT_URL = "http://reference.sitepoint.com/html/";


  @Override
  @Nullable
  public String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
    if (element instanceof SchemaPrefix) {
      return ((SchemaPrefix)element).getQuickNavigateInfo();
    }
    if (element instanceof XmlEntityDecl) {
      final XmlAttributeValue value = ((XmlEntityDecl)element).getValueElement();
      return value != null ? value.getText() : null;
    }
    return null;
  }

  @Override
  public List<String> getUrlFor(PsiElement element, PsiElement originalElement) {
    if (element instanceof XmlTag) {
      XmlTag tag = (XmlTag)element;

      MyPsiElementProcessor processor = new MyPsiElementProcessor();
      XmlUtil.processXmlElements(tag,processor, true);

      if (processor.url == null) {
        XmlTag declaration = getComplexOrSimpleTypeDefinition(element, originalElement);

        if (declaration != null) {
          XmlUtil.processXmlElements(declaration,processor, true);
        }
      }

      return processor.url != null ? Collections.singletonList(processor.url) : null;
    }
    return null;
  }

  @Override
  public String generateDoc(PsiElement element, final PsiElement originalElement) {
    if (element instanceof XmlElementDecl) {
      PsiElement curElement = findPreviousComment(element);

      if (curElement!=null) {
        return formatDocFromComment(curElement, ((XmlElementDecl)element).getNameElement().getText());
      }
    }
    else if (element instanceof XmlTag) {
      XmlTag tag = (XmlTag)element;
      MyPsiElementProcessor processor = new MyPsiElementProcessor();
      String name = tag.getAttributeValue(NAME_ATTR_NAME);
      String typeName = null;

      if (originalElement != null && originalElement.getParent() instanceof XmlAttributeValue) {
        XmlAttributeValue value = (XmlAttributeValue)originalElement.getParent();
        String toSearch = value.getValue();
        XmlTag enumerationTag;
        
        if (XmlUtil.ENUMERATION_TAG_NAME.equals(tag.getLocalName())) {
          enumerationTag = tag;
          name = enumerationTag.getAttributeValue(XmlUtil.VALUE_ATTR_NAME);
        } else {
          enumerationTag = findEnumerationValue(toSearch, tag);
          name = toSearch;
        }

        if (enumerationTag != null) {
          XmlUtil.processXmlElements(enumerationTag,processor, true);

          if (processor.result != null) {
            typeName = XmlBundle.message("xml.javadoc.enumeration.value.message");
          }
        }
      }

      if (processor.result == null) XmlUtil.processXmlElements(tag,processor, true);

      if (processor.result == null) {
        XmlTag declaration = getComplexOrSimpleTypeDefinition(element, originalElement);

        if (declaration != null) {
          XmlUtil.processXmlElements(declaration,processor, true);
          name = declaration.getAttributeValue(NAME_ATTR_NAME);
          typeName = XmlBundle.message("xml.javadoc.complex.type.message");
        }
      }
      if (processor.result == null) {
        final PsiElement comment = findPreviousComment(element);
        if (comment != null) {
          return formatDocFromComment(comment, ((XmlTag)element).getName());
        }
      }

      String doc = generateDoc(processor.result, name, typeName, processor.version);
      if (doc != null && originalElement != null) {
        doc += generateHtmlAdditionalDocTemplate(originalElement);
      }
      return doc;

    } else if (element instanceof XmlAttributeDecl) {
      // Check for comment before attlist, it should not be right after previous declaration
      final PsiElement parent = element.getParent();
      final PsiElement previousComment = findPreviousComment(parent);
      final String referenceName = ((XmlAttributeDecl)element).getNameElement().getText();

      if (previousComment instanceof PsiComment) {
        final PsiElement prevSibling = previousComment.getPrevSibling();

        if (prevSibling == null ||
            ( prevSibling instanceof PsiWhiteSpace &&
              prevSibling.getText().indexOf('\n') >= 0
            )
           ) {
          return formatDocFromComment(previousComment, referenceName);
        }
      }

      return findDocRightAfterElement(parent, referenceName);
    } else if (element instanceof XmlEntityDecl) {
      final XmlEntityDecl entityDecl = (XmlEntityDecl)element;

      return findDocRightAfterElement(element, entityDecl.getName());
    }

    return null;
  }

  private static XmlTag findEnumerationValue(final String text, XmlTag tag) {
    final Ref<XmlTag> enumerationTag = new Ref<>();

    Processor<XmlTag> processor = xmlTag -> {
      if (text.equals(xmlTag.getAttributeValue(XmlUtil.VALUE_ATTR_NAME))) {
        enumerationTag.set(xmlTag);
      }
      return true;
    };
    XmlUtil.processEnumerationValues(tag, processor);

    if (enumerationTag.get() == null) {
      final XmlElementDescriptorImpl elementDescriptor = (XmlElementDescriptorImpl)XmlUtil.findXmlDescriptorByType(
        tag,
        null
      );

      TypeDescriptor type = elementDescriptor != null ? elementDescriptor.getType():null;
      if (type instanceof ComplexTypeDescriptor) {
        XmlUtil.processEnumerationValues(((ComplexTypeDescriptor)type).getDeclaration(), processor);
      }
    }
    return enumerationTag.get();
  }

  static String generateHtmlAdditionalDocTemplate(@NotNull PsiElement element) {
    StringBuilder buf = new StringBuilder();
    final PsiFile containingFile = element.getContainingFile();
    if (containingFile != null) {
      final XmlTag tag = PsiTreeUtil.getParentOfType(element, XmlTag.class, false);
      boolean append;
      if (tag instanceof HtmlTag) {
        append = true;
      }
      else {
        final FileViewProvider provider = containingFile.getViewProvider();
        Language language;
        if (provider instanceof TemplateLanguageFileViewProvider) {
          language = ((TemplateLanguageFileViewProvider)provider).getTemplateDataLanguage();
        }
        else {
          language = provider.getBaseLanguage();
        }

        append = language == XHTMLLanguage.INSTANCE;
      }

      if (tag != null) {
        EntityDescriptor descriptor = HtmlDescriptorsTable.getTagDescriptor(tag.getName());
        if (descriptor != null && append) {
          buf.append("<br>");
          buf.append(XmlBundle.message("html.quickdoc.additional.template",
                                       descriptor.getHelpRef(),
                                       BASE_SITEPOINT_URL + tag.getName()));
        }
      }
    }

    return buf.toString();
  }

  public String findDocRightAfterElement(final PsiElement parent, final String referenceName) {
    // Check for comment right after the xml attlist decl
    PsiElement uncleElement = parent.getNextSibling();
    if (uncleElement instanceof PsiWhiteSpace && uncleElement.getText().indexOf('\n') == -1) uncleElement = uncleElement.getNextSibling();
    if (uncleElement instanceof PsiComment) {
      return formatDocFromComment(uncleElement, referenceName);
    }
    return null;
  }

  @Nullable
  public static PsiElement findPreviousComment(final PsiElement element) {
    PsiElement curElement = element;

    while(curElement!=null && !(curElement instanceof XmlComment)) {
      curElement = curElement.getPrevSibling();
      if (curElement instanceof XmlText && StringUtil.isEmptyOrSpaces(curElement.getText())) {
        continue;
      }
      if (!(curElement instanceof PsiWhiteSpace) &&
          !(curElement instanceof XmlProlog) &&
          !(curElement instanceof XmlComment)
         ) {
        curElement = null; // finding comment fails, we found another similar declaration
        break;
      }
    }
    return curElement;
  }

  private String formatDocFromComment(final PsiElement curElement, final String name) {
    String text = curElement.getText();
    text = text.substring("<!--".length(),text.length()-"-->".length()).trim();
    text = escapeDocumentationTextText(text);
    return generateDoc(text, name,null, null);
  }

  private static XmlTag getComplexOrSimpleTypeDefinition(PsiElement element, PsiElement originalElement) {
    XmlElementDescriptor descriptor = element.getUserData(DESCRIPTOR_KEY);

    XmlTag contextTag = null;

    XmlAttribute contextAttribute;

    if (descriptor == null &&
        originalElement != null &&
        (contextAttribute = PsiTreeUtil.getParentOfType(originalElement, XmlAttribute.class)) != null) {
      final XmlAttributeDescriptor attributeDescriptor = contextAttribute.getDescriptor();

      if (attributeDescriptor instanceof XmlAttributeDescriptorImpl) {
        final XmlElementDescriptorImpl elementDescriptor = (XmlElementDescriptorImpl)XmlUtil.findXmlDescriptorByType(
          (XmlTag)attributeDescriptor.getDeclaration(),
          contextAttribute.getParent()
        );

        TypeDescriptor type = elementDescriptor != null ? elementDescriptor.getType(contextAttribute) : null;

        if (type instanceof ComplexTypeDescriptor) {
          return ((ComplexTypeDescriptor)type).getDeclaration();
        }
      }
    }

    if (descriptor == null &&
        originalElement != null &&
        (contextTag = PsiTreeUtil.getParentOfType(originalElement, XmlTag.class)) != null) {
      descriptor = contextTag.getDescriptor();
    }

    if (descriptor instanceof XmlElementDescriptorImpl) {
      TypeDescriptor type = ((XmlElementDescriptorImpl)descriptor).getType(contextTag);

      if (type instanceof ComplexTypeDescriptor) {
        return ((ComplexTypeDescriptor)type).getDeclaration();
      }
    }

    return null;
  }

  protected String generateDoc(String str, String name, String typeName, String version) {
    if (str == null) return null;
    StringBuilder buf = new StringBuilder(str.length() + 20);

    DocumentationUtil.formatEntityName(typeName == null ? XmlBundle.message("xml.javadoc.tag.name.message"):typeName,name,buf);

    final String indent = "  ";
    final StringBuilder builder = buf.append(XmlBundle.message("xml.javadoc.description.message")).append(indent).
        append(HtmlDocumentationProvider.NBSP).append(str);
    if (version != null) {
      builder.append(HtmlDocumentationProvider.BR).append(XmlBundle.message("xml.javadoc.version.message")).append(indent)
          .append(HtmlDocumentationProvider.NBSP).append(version);
    }
    return builder.toString();
  }

  @Override
  public PsiElement getDocumentationElementForLookupItem(final PsiManager psiManager, Object object, PsiElement element) {

    if (object instanceof XmlExtension.TagInfo) {
      return ((XmlExtension.TagInfo)object).getDeclaration();
    }

    final PsiElement originalElement = element;
    boolean isAttrCompletion = element instanceof XmlAttribute;

    if (!isAttrCompletion && element instanceof XmlToken) {
      final IElementType tokenType = ((XmlToken)element).getTokenType();

      if (tokenType == XmlTokenType.XML_EMPTY_ELEMENT_END || tokenType == XmlTokenType.XML_TAG_END) {
        isAttrCompletion = true;
      } else if (element.getParent() instanceof XmlAttribute) {
        isAttrCompletion = true;
      }
    }

    element = PsiTreeUtil.getParentOfType(element, XmlTag.class, false);

    if (element instanceof XmlTag) {
      XmlTag xmlTag = (XmlTag)element;
      XmlElementDescriptor elementDescriptor;

      if (isAttrCompletion && object instanceof String) {
        elementDescriptor = xmlTag.getDescriptor();

        if (elementDescriptor != null) {
          final XmlAttributeDescriptor attributeDescriptor = elementDescriptor.getAttributeDescriptor((String)object, xmlTag);
          if (attributeDescriptor != null) {
            final PsiElement declaration = attributeDescriptor.getDeclaration();
            if (declaration != null) return declaration;
          }
        }
      }

      if (object == null) return null;
      try {
        @NonNls StringBuilder tagText = new StringBuilder(object.toString());
        String namespacePrefix = XmlUtil.findPrefixByQualifiedName(object.toString());
        String namespace = xmlTag.getNamespaceByPrefix(namespacePrefix);

        if (namespace!=null && namespace.length() > 0) {
          tagText.append(" xmlns");
          if (namespacePrefix.length() > 0) tagText.append(":").append(namespacePrefix);
          tagText.append("=\"").append(namespace).append("\"");
        }

        XmlTag tagFromText = XmlElementFactory.getInstance(xmlTag.getProject()).createTagFromText("<" + tagText +"/>");
        XmlElementDescriptor parentDescriptor = xmlTag.getDescriptor();
        elementDescriptor = (parentDescriptor!=null)?parentDescriptor.getElementDescriptor(tagFromText, xmlTag):null;

        if (elementDescriptor==null) {
          PsiElement parent = xmlTag.getParent();
          if (parent instanceof XmlTag) {
            parentDescriptor = ((XmlTag)parent).getDescriptor();
            elementDescriptor = (parentDescriptor!=null)?parentDescriptor.getElementDescriptor(tagFromText, (XmlTag)parent):null;
          }
        }

        if (elementDescriptor instanceof AnyXmlElementDescriptor) {
          final XmlNSDescriptor nsDescriptor = xmlTag.getNSDescriptor(xmlTag.getNamespaceByPrefix(namespacePrefix), true);
          elementDescriptor = (nsDescriptor != null)?nsDescriptor.getElementDescriptor(tagFromText):null;
        }

        // The very special case of xml file
        final PsiFile containingFile = xmlTag.getContainingFile();
        final XmlFile xmlFile = XmlUtil.getContainingFile(xmlTag);
        if (xmlFile != containingFile) {
          final XmlTag rootTag = xmlFile.getDocument().getRootTag();
          if (rootTag != null) {
            final XmlNSDescriptor nsDescriptor = rootTag.getNSDescriptor(rootTag.getNamespaceByPrefix(namespacePrefix), true);
            elementDescriptor = (nsDescriptor != null) ? nsDescriptor.getElementDescriptor(tagFromText) : null;
          }
        }

        if (elementDescriptor != null) {
          PsiElement declaration = elementDescriptor.getDeclaration();
          if (declaration!=null) declaration.putUserData(DESCRIPTOR_KEY,elementDescriptor);
          return declaration;
        }
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }

    if (object instanceof String && originalElement != null) {
      PsiElement result = findDeclWithName((String)object, originalElement);

      if (result == null && element instanceof XmlTag) {
        XmlAttribute attribute = PsiTreeUtil.getParentOfType(originalElement, XmlAttribute.class, false);
        if (attribute != null) {
          XmlAttributeDescriptor descriptor = attribute.getDescriptor();
          if (descriptor != null && descriptor.getDeclaration() instanceof XmlTag) {
            result = findEnumerationValue((String)object, (XmlTag)descriptor.getDeclaration());
          }
        }
      }
      return result;
    }
    if (object instanceof XmlElementDescriptor) {
      return ((XmlElementDescriptor)object).getDeclaration();
    }
    return null;
  }

  public static PsiElement findDeclWithName(final String name, final @NotNull PsiElement element) {
    final XmlFile containingXmlFile = XmlUtil.getContainingFile(element);
    final XmlTag nearestTag = PsiTreeUtil.getParentOfType(element, XmlTag.class, false);
    final XmlFile xmlFile = nearestTag != null && containingXmlFile != null ? XmlCompletionData.findDescriptorFile(nearestTag, containingXmlFile):containingXmlFile;

    if (xmlFile != null) {
      final PsiElement[] result = new PsiElement[1];

      XmlUtil.processXmlElements(
        xmlFile,
        new PsiElementProcessor() {
          @Override
          public boolean execute(@NotNull final PsiElement element) {
            if (element instanceof XmlEntityDecl) {
              final XmlEntityDecl entityDecl = (XmlEntityDecl)element;
              if (entityDecl.isInternalReference() && name.equals(entityDecl.getName())) {
                result[0] = entityDecl;
                return false;
              }
            } else if (element instanceof XmlElementDecl) {
              final XmlElementDecl entityDecl = (XmlElementDecl)element;
              if (name.equals(entityDecl.getName())) {
                result[0] = entityDecl;
                return false;
              }
            }
            return true;
          }
        },
        true
      );

      return result[0];
    }
    return null;
  }

  @Override
  public PsiElement getDocumentationElementForLink(final PsiManager psiManager, String link, PsiElement context) {
    return null;
  }

  private static class MyPsiElementProcessor implements PsiElementProcessor {
    String result;
    String version;
    String url;
    @NonNls public static final String DOCUMENTATION_ELEMENT_LOCAL_NAME = "documentation";
    private @NonNls static final String CDATA_PREFIX = "<![CDATA[";
    private @NonNls static final String CDATA_SUFFIX = "]]>";

    @Override
    public boolean execute(@NotNull PsiElement element) {
      if (element instanceof XmlTag &&
          ((XmlTag)element).getLocalName().equals(DOCUMENTATION_ELEMENT_LOCAL_NAME)
      ) {
        final XmlTag tag = ((XmlTag)element);
        result = tag.getValue().getText().trim();
        boolean withCData = false;

        if (result.startsWith(CDATA_PREFIX)) {
          result = result.substring(CDATA_PREFIX.length());
          withCData = true;
        }

        result = StringUtil.trimEnd(result, CDATA_SUFFIX);
        result = result.trim();

        if (withCData) {
          result = escapeDocumentationTextText(result);
        }

        final @NonNls String s = tag.getAttributeValue("source");
        if (s != null) {
          if (s.startsWith("http:")) url = s;
          else if ("version".equals(s)) {
            version = result;
            result = null;
            return true;
          }
        }
        return false;
      }
      return true;
    }
  }

  private static String escapeDocumentationTextText(final String result) {
    return StringUtil.escapeXml(result).replaceAll("&apos;","'").replaceAll("\n","<br>\n");
  }
}
