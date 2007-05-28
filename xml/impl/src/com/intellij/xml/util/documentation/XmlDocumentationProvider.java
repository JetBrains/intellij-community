package com.intellij.xml.util.documentation;

import com.intellij.codeInsight.completion.XmlCompletionData;
import com.intellij.codeInsight.javadoc.JavaDocUtil;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.lang.documentation.MetaDataDocumentationProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlBundle;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.impl.schema.AnyXmlElementDescriptor;
import com.intellij.xml.impl.schema.ComplexTypeDescriptor;
import com.intellij.xml.impl.schema.TypeDescriptor;
import com.intellij.xml.impl.schema.XmlElementDescriptorImpl;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: maxim
 * Date: 25.12.2004
 * Time: 0:00:05
 * To change this template use File | Settings | File Templates.
 */
public class XmlDocumentationProvider implements DocumentationProvider {
  private static final Key<XmlElementDescriptor> DESCRIPTOR_KEY = Key.create("Original element");

  private static final Logger LOG = Logger.getInstance("#com.intellij.xml.util.documentation.XmlDocumentationProvider");

  private final DocumentationProvider myDocumentationProvider = new MetaDataDocumentationProvider();
  @NonNls private static final String NAME_ATTR_NAME = "name";

  @Nullable
  public String getQuickNavigateInfo(PsiElement element) {
    return myDocumentationProvider.getQuickNavigateInfo(element);
  }

  public String getUrlFor(PsiElement element, PsiElement originalElement) {
    if (element instanceof XmlTag) {
      XmlTag tag = (XmlTag)element;

      MyPsiElementProcessor processor = new MyPsiElementProcessor();
      XmlUtil.processXmlElements(tag,processor, true);

      if (processor.url == null) {
        XmlTag declaration = getComplexTypeDefinition(element, originalElement);

        if (declaration != null) {
          XmlUtil.processXmlElements(declaration,processor, true);
        }
      }

      return processor.url;
    }

    return null;
  }

  public String generateDoc(PsiElement element, PsiElement originalElement) {
    if (element instanceof XmlElementDecl) {
      PsiElement curElement = findPreviousComment(element);

      if (curElement!=null) {
        return formatDocFromComment(curElement, ((XmlElementDecl)element).getNameElement().getText());
      }
    } else if (element instanceof XmlTag) {
      XmlTag tag = (XmlTag)element;

      MyPsiElementProcessor processor = new MyPsiElementProcessor();
      XmlUtil.processXmlElements(tag,processor, true);
      String name = tag.getAttributeValue(NAME_ATTR_NAME);
      String typeName = null;

      if (processor.result == null) {
        XmlTag declaration = getComplexTypeDefinition(element, originalElement);

        if (declaration != null) {
          XmlUtil.processXmlElements(declaration,processor, true);
          name = declaration.getAttributeValue(NAME_ATTR_NAME);
          typeName = declaration.getName();
        }
      }

      return generateDoc(processor.result, name, typeName);
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

  public static String findDocRightAfterElement(final PsiElement parent, final String referenceName) {
    // Check for comment right after the xml attlist decl
    PsiElement uncleElement = parent.getNextSibling();
    if (uncleElement instanceof PsiWhiteSpace && uncleElement.getText().indexOf('\n') == -1) uncleElement = uncleElement.getNextSibling();
    if (uncleElement instanceof PsiComment) {
      return formatDocFromComment(uncleElement, referenceName);
    }
    return null;
  }

  private static PsiElement findPreviousComment(final PsiElement element) {
    PsiElement curElement = element;

    while(curElement!=null && !(curElement instanceof XmlComment)) {
      curElement = curElement.getPrevSibling();
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

  private static String formatDocFromComment(final PsiElement curElement, final String name) {
    String text = curElement.getText();
    text = text.substring("<!--".length(),text.length()-"-->".length()).trim();
    return generateDoc(text, name,null);
  }

  private XmlTag getComplexTypeDefinition(PsiElement element, PsiElement originalElement) {
    XmlElementDescriptor descriptor = element.getUserData(DESCRIPTOR_KEY);

    XmlTag contextTag;

    if (descriptor == null &&
        originalElement != null &&
        (contextTag = PsiTreeUtil.getParentOfType(originalElement, XmlTag.class)) != null) {
      descriptor = contextTag.getDescriptor();
    }

    if (descriptor instanceof XmlElementDescriptorImpl) {
      TypeDescriptor type = ((XmlElementDescriptorImpl)descriptor).getType();

      if (type instanceof ComplexTypeDescriptor) {
        return ((ComplexTypeDescriptor)type).getDeclaration();
      }
    }

    return null;
  }

  private static String generateDoc(String str, String name, String typeName) {
    if (str == null) return null;
    StringBuffer buf = new StringBuffer(str.length() + 20);

    if (typeName==null) {
      JavaDocUtil.formatEntityName(XmlBundle.message("xml.javadoc.tag.name.message"),name,buf);
    } else {
      JavaDocUtil.formatEntityName(XmlBundle.message("xml.javadoc.complex.type.message"),name,buf);
    }

    return buf.append(XmlBundle.message("xml.javadoc.description.message")).append("  ").
      append(HtmlDocumentationProvider.NBSP).append(str).toString();
  }

  public PsiElement getDocumentationElementForLookupItem(final PsiManager psiManager, Object object, PsiElement element) {
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

      try {
        @NonNls StringBuffer tagText = new StringBuffer(object.toString());
        String namespacePrefix = XmlUtil.findPrefixByQualifiedName(object.toString());
        String namespace = xmlTag.getNamespaceByPrefix(namespacePrefix);

        if (namespace!=null && namespace.length() > 0) {
          tagText.append(" xmlns");
          if (namespacePrefix.length() > 0) tagText.append(":").append(namespacePrefix);
          tagText.append("=\"").append(namespace).append("\"");
        }

        XmlTag tagFromText = xmlTag.getManager().getElementFactory().createTagFromText("<" + tagText +"/>");
        XmlElementDescriptor parentDescriptor = xmlTag.getDescriptor();
        elementDescriptor = (parentDescriptor!=null)?parentDescriptor.getElementDescriptor(tagFromText):null;

        if (elementDescriptor==null) {
          PsiElement parent = xmlTag.getParent();
          if (parent instanceof XmlTag) {
            parentDescriptor = ((XmlTag)parent).getDescriptor();
            elementDescriptor = (parentDescriptor!=null)?parentDescriptor.getElementDescriptor(tagFromText):null;
          }
        }

        if (elementDescriptor instanceof AnyXmlElementDescriptor) {
          final XmlNSDescriptor nsDescriptor = xmlTag.getNSDescriptor(xmlTag.getNamespaceByPrefix(namespacePrefix), true);
          elementDescriptor = (nsDescriptor != null)?nsDescriptor.getElementDescriptor(tagFromText):null;
        }

        // The very special case of xml file 
        final PsiFile containingFile = xmlTag.getContainingFile();
        if (PsiUtil.isInJspFile(containingFile)) {
          final XmlTag rootTag = ((XmlFile)containingFile).getDocument().getRootTag();
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

    if (object instanceof String) {
      return findEntityDeclWithName((String)object, element);
    }
    return null;
  }

  public static PsiElement findEntityDeclWithName(final String name, final @NotNull PsiElement element) {
    final XmlFile containingXmlFile = XmlUtil.getContainingFile(element);
    final XmlTag nearestTag = PsiTreeUtil.getParentOfType(element, XmlTag.class, false);
    final XmlFile xmlFile = nearestTag != null? XmlCompletionData.findDescriptorFile(nearestTag, containingXmlFile):containingXmlFile;

    if (xmlFile != null) {
      final PsiElement[] result = new PsiElement[1];

      XmlUtil.processXmlElements(
        xmlFile,
        new PsiElementProcessor() {
          public boolean execute(final PsiElement element) {
            if (element instanceof XmlEntityDecl) {
              final XmlEntityDecl entityDecl = (XmlEntityDecl)element;
              if (entityDecl.isInternalReference() && name.equals(entityDecl.getName())) {
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

  public PsiElement getDocumentationElementForLink(final PsiManager psiManager, String link, PsiElement context) {
    return null;
  }

  private static class MyPsiElementProcessor implements PsiElementProcessor {
    String result;
    String url;
    @NonNls public static final String DOCUMENTATION_ELEMENT_LOCAL_NAME = "documentation";
    private @NonNls static final String CDATA_PREFIX = "<![CDATA[";
    private @NonNls static final String CDATA_SUFFIX = "]]>";

    public boolean execute(PsiElement element) {
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

        if (result.endsWith(CDATA_SUFFIX)) {
          result = result.substring(0, result.length() - CDATA_SUFFIX.length());
        }
        result = result.trim();

        if (withCData) {
          result = XmlUtil.escape(result).replaceAll("&apos;","'").replaceAll("\n","<br>");
        }
        url = tag.getAttributeValue("source");
        return false;
      }
      return true;
    }

  }
}
