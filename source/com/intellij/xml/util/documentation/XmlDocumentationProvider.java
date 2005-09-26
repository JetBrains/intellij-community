package com.intellij.xml.util.documentation;

import com.intellij.ant.AntConfiguration;
import com.intellij.ant.BuildFile;
import com.intellij.ant.impl.AntInstallation;
import com.intellij.ant.impl.references.PsiNoWhereElement;
import com.intellij.codeInsight.javadoc.JavaDocManager;
import com.intellij.codeInsight.javadoc.JavaDocUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlComment;
import com.intellij.psi.xml.XmlElementDecl;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.impl.schema.AnyXmlElementDescriptor;
import com.intellij.xml.impl.schema.ComplexTypeDescriptor;
import com.intellij.xml.impl.schema.TypeDescriptor;
import com.intellij.xml.impl.schema.XmlElementDescriptorImpl;
import com.intellij.xml.util.XmlUtil;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;

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
        XmlTag declaration = getComplexTypeDefinition(element, originalElement);

        if (declaration != null) {
          XmlUtil.processXmlElements(declaration,processor, true);
          typeName = declaration.getName();
        }
      }

      return generateDoc(processor.result, name, typeName);
    } else if (element instanceof PsiNoWhereElement) {
      PsiFile containingFile = originalElement.getContainingFile();
      AntConfiguration instance = AntConfiguration.getInstance(originalElement.getProject());

      for(Iterator<BuildFile> i = instance.getBuildFiles(); i.hasNext();) {
        BuildFile buildFile = i.next();

        if (buildFile.getXmlFile().equals(containingFile)) {
          AntInstallation installation = BuildFile.ANT_INSTALLATION.get(buildFile.getAllOptions());

          if (installation != null) {
            String path = AntInstallation.HOME_DIR.get(installation.getProperties());
            path += "/docs/manual";
            XmlTag tag = PsiTreeUtil.getParentOfType(originalElement, XmlTag.class);
            final String helpFileShortName = tag.getName() + ".html";

            if (tag == null) return null;
            File file = new File(path);
            File helpFile = null;

            if (file.exists()) {
              File candidateHelpFile = new File(path + "/CoreTasks/" + helpFileShortName);
              if (candidateHelpFile.exists()) helpFile = candidateHelpFile;

              if (helpFile == null) {
                candidateHelpFile = new File(path + "/CoreTypes/" + helpFileShortName);
                if (candidateHelpFile.exists()) helpFile = candidateHelpFile;
              }

              if (helpFile == null) {
                candidateHelpFile = new File(path + "/OptionalTasks/" + helpFileShortName);
                if (candidateHelpFile.exists()) helpFile = candidateHelpFile;
              }

              if (helpFile == null) {
                candidateHelpFile = new File(path + "/OptionalTypes/" + helpFileShortName);
                if (candidateHelpFile.exists()) helpFile = candidateHelpFile;
              }
            }

            if (helpFile != null) {
              final File helpFile1 = helpFile;
              VirtualFile fileByIoFile = ApplicationManager.getApplication().runReadAction(
                new Computable<VirtualFile>() {
                  public VirtualFile compute() {
                    return LocalFileSystem.getInstance().findFileByIoFile(helpFile1);
                  }
                }
              );
              
              if (fileByIoFile != null) {
                try {
                  return new String(fileByIoFile.contentsToCharArray());
                } catch(IOException ex) {}
              }
            }
          }
        }
      }
    }

    return null;
  }

  private XmlTag getComplexTypeDefinition(PsiElement element, PsiElement originalElement) {
    XmlElementDescriptor descriptor = element.getUserData(DESCRIPTOR_KEY);

    if (descriptor == null &&
        originalElement != null &&
        originalElement.getParent() instanceof XmlTag) {
      descriptor = ((XmlTag)originalElement.getParent()).getDescriptor();
    }

    if (descriptor instanceof XmlElementDescriptorImpl) {
      TypeDescriptor type = ((XmlElementDescriptorImpl)descriptor).getType();

      if (type instanceof ComplexTypeDescriptor) {
        return ((ComplexTypeDescriptor)type).getDeclaration();
      }
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
        String namespacePrefix = XmlUtil.findPrefixByQualifiedName(tagText);
        String namespace = xmlTag.getNamespaceByPrefix(namespacePrefix);

        if (namespace!=null && namespace.length() > 0) {
          tagText+=" xmlns";
          if (namespacePrefix.length() > 0) tagText += ":" + namespacePrefix;
          tagText +="=\""+namespace+"\"";
        }

        tagText = "<" + tagText +"/>";

        XmlTag tagFromText = xmlTag.getManager().getElementFactory().createTagFromText(tagText);
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

        if (elementDescriptor != null) {
          PsiElement declaration = elementDescriptor.getDeclaration();
          if (declaration!=null) declaration.putUserData(DESCRIPTOR_KEY,elementDescriptor);
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
    String url;

    public boolean execute(PsiElement element) {
      if (element instanceof XmlTag &&
          ((XmlTag)element).getLocalName().equals("documentation")
      ) {
        final XmlTag tag = ((XmlTag)element);
        result = tag.getValue().getText().trim();
        url = tag.getAttributeValue("source");
        return false;
      }
      return true;
    }

  }
}
