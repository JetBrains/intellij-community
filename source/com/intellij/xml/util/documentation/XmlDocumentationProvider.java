package com.intellij.xml.util.documentation;

import com.intellij.codeInsight.javadoc.JavaDocManager;
import com.intellij.codeInsight.javadoc.JavaDocUtil;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.util.XmlUtil;
import com.intellij.xml.impl.schema.XmlElementDescriptorImpl;
import com.intellij.xml.impl.schema.TypeDescriptor;
import com.intellij.xml.impl.schema.ComplexTypeDescriptor;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlElementDecl;
import com.intellij.psi.xml.XmlComment;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;

/**
 * Created by IntelliJ IDEA.
 * User: maxim
 * Date: 25.12.2004
 * Time: 0:00:05
 * To change this template use File | Settings | File Templates.
 */
public class XmlDocumentationProvider implements JavaDocManager.DocumentationProvider {
  private static final Key<XmlElementDescriptor> DESCRIPTOR_KEY = Key.create("Original element");

  public String getUrlFor(PsiElement element, PsiElement originalElement) {
    return null;
  }

  public String generateDoc(PsiElement element, PsiElement originalElement) {
    if (element instanceof XmlElementDecl) {
      PsiElement curElement = element;

      while(curElement!=null && !(curElement instanceof XmlComment)) {
        curElement = curElement.getPrevSibling();
        if (curElement!=null && curElement.getClass() == element.getClass()) {
          curElement = null; // finding comment fails, we found another similar declaration
          break;
        }
      }

      if (curElement!=null) {
        String text = curElement.getText();
        text = text.substring("<!--".length(),text.length()-"-->".length()).trim();
        return generateDoc(text,((XmlElementDecl)element).getNameElement().getText(),null);
      }
    } else if (element instanceof XmlTag) {
      XmlTag tag = (XmlTag)element;

      MyPsiElementProcessor processor = new MyPsiElementProcessor();
      XmlUtil.processXmlElements(tag,processor, true);
      String name = tag.getAttributeValue("name");
      String typeName = null;

      if (processor.result == null) {
        XmlElementDescriptor descriptor = element.getUserData(DESCRIPTOR_KEY);
        if (descriptor == null && originalElement.getParent() instanceof XmlTag) {
          descriptor = ((XmlTag)originalElement.getParent()).getDescriptor();
        }

        if (descriptor instanceof XmlElementDescriptorImpl) {
          TypeDescriptor type = ((XmlElementDescriptorImpl)descriptor).getType();

          if (type instanceof ComplexTypeDescriptor) {
            XmlTag declaration = ((ComplexTypeDescriptor)type).getDeclaration();
            XmlUtil.processXmlElements(declaration,processor, true);
            typeName = declaration.getName();
          }
        }
      }

      return generateDoc(processor.result, name, typeName);
    }

    return null;
  }

  private String generateDoc(String str, String name, String typeName) {
    if (str == null) return null;
    StringBuffer buf = new StringBuffer(str.length() + 20);

    if (typeName==null) {
      JavaDocUtil.formatEntityName("Tag name",name,buf);
    } else {
      JavaDocUtil.formatEntityName("Complex type",name,buf);
    }

    buf.append("Description  :&nbsp;").append(str);

    return buf.toString();
  }

  public PsiElement getDocumentationElementForLookupItem(Object object, PsiElement element) {
    element = PsiTreeUtil.getParentOfType(element, XmlTag.class, false);

    if (element instanceof XmlTag) {
      XmlTag xmlTag = (XmlTag)element;
      XmlElementDescriptor elementDescriptor = null;

      try {
        String tagText = object.toString();
        String namespace = xmlTag.getNamespace();

        if (namespace!=null && namespace.length() > 0) {
          tagText+=" xmlns=\""+namespace+"\"";
        }

        tagText = "<" + tagText +"/>";

        XmlTag tagFromText = xmlTag.getManager().getElementFactory().createTagFromText(tagText);
        elementDescriptor = xmlTag.getDescriptor().getElementDescriptor(tagFromText);

        if (elementDescriptor==null) {
          PsiElement parent = xmlTag.getParent();
          if (parent instanceof XmlTag) {
            elementDescriptor = ((XmlTag)parent).getDescriptor().getElementDescriptor(tagFromText);
          }
        }

        if (elementDescriptor!=null) {
          PsiElement declaration = elementDescriptor.getDeclaration();
          declaration.putUserData(DESCRIPTOR_KEY,elementDescriptor);
          return declaration;
        }
      }
      catch (IncorrectOperationException e) {
        e.printStackTrace();
      }
    }
    return null;
  }

  public PsiElement getDocumentationElementForLink(String link, PsiElement context) {
    return null;
  }

  private static class MyPsiElementProcessor implements PsiElementProcessor {
    String result;

    public boolean execute(PsiElement element) {
      if (element instanceof XmlTag &&
          ((XmlTag)element).getLocalName().equals("documentation")
      ) {
        result = ((XmlTag)element).getValue().getText().trim();
        return false;
      }
      return true;
    }

    public Object getHint(Class hintClass) {
      return null;
    }
  }
}
