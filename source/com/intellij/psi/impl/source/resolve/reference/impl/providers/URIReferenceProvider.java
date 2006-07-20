package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.j2ee.openapi.ex.ExternalResourceManagerEx;
import com.intellij.javaee.ExternalResourceManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.impl.source.jsp.JspManager;
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.XmlNSDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

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

  public static class URLReference implements PsiReference {
    private PsiElement myElement;
    @NonNls private static final String TARGET_NAMESPACE_ATTR_NAME = "targetNamespace";

    public URLReference(PsiElement element) {
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
      if (ExternalResourceManagerEx.getInstanceEx().isIgnoredResource(canonicalText)) return myElement;
      VirtualFile relativeFile = VfsUtil.findRelativeFile(canonicalText, myElement.getContainingFile().getVirtualFile());
      if (relativeFile != null) return myElement.getManager().findFile(relativeFile);

      final XmlTag tag = PsiTreeUtil.getParentOfType(myElement, XmlTag.class);
      if (tag != null && canonicalText.equals(tag.getAttributeValue(TARGET_NAMESPACE_ATTR_NAME))) return tag;

      final PsiFile containingFile = myElement.getContainingFile();
      if (containingFile instanceof XmlFile) {
        final XmlTag rootTag = ((XmlFile)containingFile).getDocument().getRootTag();
        if (rootTag == null) return null;
        final XmlNSDescriptor nsDescriptor = rootTag.getNSDescriptor(canonicalText, true);
        if (nsDescriptor != null) return nsDescriptor.getDescriptorFile();
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
      final String[] resourceUrls = ExternalResourceManager.getInstance().getResourceUrls(null, true);
      final PsiFile containingFile = myElement.getContainingFile();

      if (PsiUtil.isInJspFile(containingFile)) {
        final Object[] possibleTldUris = JspManager.getInstance(containingFile.getProject()).getPossibleTldUris(
          PsiUtil.getJspFile(containingFile));
        Object[] result = new Object[resourceUrls.length + possibleTldUris.length + 1];
        System.arraycopy(resourceUrls, 0, result, 0, resourceUrls.length);
        System.arraycopy(possibleTldUris, 0, result, resourceUrls.length, possibleTldUris.length);
        result[result.length - 1] = JspManager.TAG_DIR_NS_PREFIX + "/WEB-INF/tags";
        return result;
      }
      return resourceUrls;
    }

    public boolean isSoft() {
      return true;
    }
  }

  @NotNull
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
      if (!s.startsWith(JspManager.TAG_DIR_NS_PREFIX)) return getUrlReference(element);
      else {
        final int offset = text.indexOf(s);
        s = s.substring(JspManager.TAG_DIR_NS_PREFIX.length());
        return new FileReferenceSet(
          s,
          element,
          offset + JspManager.TAG_DIR_NS_PREFIX.length(),
          ReferenceType.FILE_TYPE,
          this,
          true
        ).getAllReferences();
      }
    } else {
      if (s.startsWith("file:")) s = s.substring("file:".length());
      return new FileReferenceSet(s,element,text.indexOf(s), ReferenceType.FILE_TYPE, this,true).getAllReferences();
    }
  }

  public PsiReference[] getUrlReference(final PsiElement element) {
    return new PsiReference[] { new URLReference(element)};
  }

  @NotNull
  public PsiReference[] getReferencesByElement(PsiElement element, ReferenceType type) {
    return PsiReference.EMPTY_ARRAY;
  }

  @NotNull
  public PsiReference[] getReferencesByString(String str, PsiElement position, ReferenceType type, int offsetInPosition) {
    return PsiReference.EMPTY_ARRAY;
  }

  public void handleEmptyContext(PsiScopeProcessor processor, PsiElement position) {
  }
}
