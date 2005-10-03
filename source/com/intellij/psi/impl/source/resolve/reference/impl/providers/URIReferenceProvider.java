package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.j2ee.ExternalResourceManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.XmlNSDescriptor;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Jul 4, 2005
 * Time: 6:56:19 PM
 * To change this template use File | Settings | File Templates.
 */
public class URIReferenceProvider implements PsiReferenceProvider {
  public ElementFilter getNamespaceAttributeFilter() {
    return new ElementFilter() {
      public boolean isAcceptable(Object element, PsiElement context) {
        final PsiElement parent = context.getParent();
        if (parent instanceof XmlAttribute) {
          final XmlAttribute attribute = ((XmlAttribute)parent);
          return attribute.isNamespaceDeclaration();
        }
        return false;
      }

      public boolean isClassAcceptable(Class hintClass) {
        return true;
      }
    };
  }

  static class URLReference implements PsiReference {
    private PsiElement myElement;
    URLReference(PsiElement element) {
      myElement = element;
    }

    public PsiElement getElement() {
      return myElement;
    }

    public TextRange getRangeInElement() {
      return new TextRange(1,myElement.getTextLength()-1);
    }

    @Nullable
    public PsiElement resolve() {
      final String canonicalText = getCanonicalText();
      VirtualFile relativeFile = VfsUtil.findRelativeFile(canonicalText, myElement.getContainingFile().getVirtualFile());
      if (relativeFile != null) return myElement.getManager().findFile(relativeFile);

      final PsiFile containingFile = myElement.getContainingFile();
      if (containingFile instanceof XmlFile) {
        final XmlTag rootTag = ((XmlFile)containingFile).getDocument().getRootTag();
        if (rootTag == null) return null;
        final XmlNSDescriptor nsDescriptor = rootTag.getNSDescriptor(canonicalText, true);
        if (nsDescriptor != null) return nsDescriptor.getDescriptorFile();
        if (canonicalText.equals(rootTag.getAttributeValue("targetNamespace"))) return containingFile;
      }
      return null;
    }

    public String getCanonicalText() {
      String text = myElement.getText();
      return text.substring(1,text.length() - 1);
    }

    public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
      return getElement();
    }

    public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException {
      return getElement();
    }

    public boolean isReferenceTo(PsiElement element) {
      return myElement.getManager().areElementsEquivalent(resolve(),element);
    }

    public Object[] getVariants() {
      return ExternalResourceManager.getInstance().getResourceUrls(null, true);
    }

    public boolean isSoft() {
      return true;
    }
  }
  
  @SuppressWarnings({"HardCodedStringLiteral"})
  public PsiReference[] getReferencesByElement(PsiElement element) {
    final String text = element.getText();
    String s = StringUtil.stripQuotesAroundValue(text);
    final PsiElement parent = element.getParent();

    if (s.startsWith("http://") || 
        s.startsWith("urn:") ||
        ( parent instanceof XmlAttribute &&
          ((XmlAttribute)parent).isNamespaceDeclaration()
        )
       ) {
      return getUrlReference(element);
    } else {
      if (s.startsWith("file:")) s = s.substring("file:".length());
      return new FileReferenceSet(s,element,text.indexOf(s), ReferenceType.FILE_TYPE, this,true).getAllReferences();
    }
  }

  public PsiReference[] getUrlReference(final PsiElement element) {
    return new PsiReference[] { new URLReference(element)};
  }

  public PsiReference[] getReferencesByElement(PsiElement element, ReferenceType type) {
    return PsiReference.EMPTY_ARRAY;
  }

  public PsiReference[] getReferencesByString(String str, PsiElement position, ReferenceType type, int offsetInPosition) {
    return PsiReference.EMPTY_ARRAY;
  }

  public void handleEmptyContext(PsiScopeProcessor processor, PsiElement position) {
  }
}
