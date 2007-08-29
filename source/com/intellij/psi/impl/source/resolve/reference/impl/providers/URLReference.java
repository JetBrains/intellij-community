/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.ElementManipulator;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.impl.source.jsp.JspManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.codeInsight.daemon.QuickFixProvider;
import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.codeInsight.daemon.XmlErrorMessages;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.daemon.impl.quickfix.FetchExtResourceAction;
import com.intellij.codeInsight.daemon.impl.quickfix.ManuallySetupExtResourceAction;
import com.intellij.codeInsight.daemon.impl.quickfix.IgnoreExtResourceAction;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.j2ee.openapi.ex.ExternalResourceManagerEx;
import com.intellij.javaee.ExternalResourceManager;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.util.Processor;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

/**
 * @author Dmitry Avdeev
*/
public class URLReference implements PsiReference, QuickFixProvider, EmptyResolveMessageProvider {

  @NonNls private static final String TARGET_NAMESPACE_ATTR_NAME = "targetNamespace";

  private final PsiElement myElement;
  private TextRange myRange;
  private boolean mySoft;

  public URLReference(PsiElement element) {
    myElement = element;
  }

  public URLReference(PsiElement element, @Nullable TextRange range, boolean soft) {
    myElement = element;
    myRange = range;
    mySoft = soft;
  }

  public PsiElement getElement() {
    return myElement;
  }

  public TextRange getRangeInElement() {
    return myRange != null ? myRange : new TextRange(1,myElement.getTextLength()-1);
  }

  @Nullable
  public PsiElement resolve() {
    final String canonicalText = getCanonicalText();

    if (canonicalText.length() == 0) {
      final XmlAttribute attr = PsiTreeUtil.getParentOfType(getElement(), XmlAttribute.class);

      if (attr != null &&
          attr.isNamespaceDeclaration() &&
          attr.getNamespacePrefix().length() == 0
         ) {
        // Namespaces in XML 1.0 2nd edition, Section 6.2, last paragraph
        // The attribute value in a default namespace declaration MAY be empty. This has the same effect, within the scope of the declaration,
        // of there being no default namespace
        return myElement;
      }
      return null;
    }

    if (ExternalResourceManagerEx.getInstanceEx().isIgnoredResource(canonicalText)) return myElement;
    final XmlTag tag = PsiTreeUtil.getParentOfType(myElement, XmlTag.class);
    if (tag != null && canonicalText.equals(tag.getAttributeValue(TARGET_NAMESPACE_ATTR_NAME))) return tag;

    final PsiFile containingFile = myElement.getContainingFile();
    if (containingFile instanceof XmlFile) {
      final XmlDocument document = ((XmlFile)containingFile).getDocument();
      assert document != null;
      final XmlTag rootTag = document.getRootTag();
      if (rootTag == null) return null;
      final XmlNSDescriptor nsDescriptor = rootTag.getNSDescriptor(canonicalText, true);
      if (nsDescriptor != null) return nsDescriptor.getDescriptorFile();

      final PsiElement[] result = new PsiElement[1];
      processWsdlSchemas(rootTag,new Processor<XmlTag>() {
        public boolean process(final XmlTag t) {
          if (canonicalText.equals(t.getAttributeValue(TARGET_NAMESPACE_ATTR_NAME))) {
            result[0] = t;
            return false;
          }
          return true;
        }
      });

      return result[0];
    }
    return null;
  }

  public String getCanonicalText() {
    final String text = myElement.getText();
    if (text.length() > 1) {
      return myRange == null ?
             text.substring(1,text.length() - 1):
             text.substring(myRange.getStartOffset(),myRange.getEndOffset());
    }

    return "";
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    final TextRange textRangeInElement = getRangeInElement();
    final PsiElement elementToChange = myElement.findElementAt(textRangeInElement.getStartOffset());
    assert elementToChange != null;
    final ElementManipulator<PsiElement> manipulator =
      ReferenceProvidersRegistry.getInstance(myElement.getProject()).getManipulator(elementToChange);
    assert manipulator != null;
    final int offsetFromStart = myElement.getTextRange().getStartOffset() + textRangeInElement.getStartOffset() - elementToChange.getTextOffset();

    manipulator.handleContentChange(elementToChange, new TextRange(offsetFromStart, offsetFromStart + textRangeInElement.getLength()),newElementName);
    return myElement;
  }

  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    assert element instanceof PsiFile;

    if (!URIReferenceProvider.isUrlText(getCanonicalText())) {
      // TODO: this should work!
      final VirtualFile virtualFile = ((PsiFile)element).getVirtualFile();
      assert virtualFile != null;
      handleElementRename(VfsUtil.fixIDEAUrl(virtualFile.getPresentableUrl()));
    }
    return myElement;
  }

  public boolean isReferenceTo(PsiElement element) {
    return myElement.getManager().areElementsEquivalent(resolve(),element);
  }

  public Object[] getVariants() {
    String[] resourceUrls = ExternalResourceManager.getInstance().getResourceUrls(null, true);
    final PsiFile containingFile = myElement.getContainingFile();

    if (PsiUtil.isInJspFile(containingFile)) {
      final JspManager jspManager = JspManager.getInstance(containingFile.getProject());
      if (jspManager != null) {
        final Object[] possibleTldUris = jspManager.getPossibleTldUris(
          PsiUtil.getJspFile(containingFile));
        @NonNls Object[] result = new Object[resourceUrls.length + possibleTldUris.length + 1];
        System.arraycopy(resourceUrls, 0, result, 0, resourceUrls.length);
        System.arraycopy(possibleTldUris, 0, result, resourceUrls.length, possibleTldUris.length);
        result[result.length - 1] = JspManager.TAG_DIR_NS_PREFIX + "/WEB-INF/tags";
        return result;
      }
    } else if (containingFile instanceof XmlFile) {
      final XmlDocument document = ((XmlFile)containingFile).getDocument();
      assert document != null;
      XmlTag rootTag = document.getRootTag();
      final ArrayList<String> additionalNs = new ArrayList<String>();
      processWsdlSchemas(rootTag, new Processor<XmlTag>() {
        public boolean process(final XmlTag xmlTag) {
          final String s = xmlTag.getAttributeValue(TARGET_NAMESPACE_ATTR_NAME);
          if (s != null) { additionalNs.add(s); }
          return true;
        }
      });
      resourceUrls = ArrayUtil.mergeArrays(resourceUrls, additionalNs.toArray(new String[additionalNs.size()]), String.class);
    }
    return resourceUrls;
  }

  public boolean isSoft() {
    return mySoft;
  }

  public void registerQuickfix(HighlightInfo info, PsiReference reference) {
    QuickFixAction.registerQuickFixAction(info, new FetchExtResourceAction());
    QuickFixAction.registerQuickFixAction(info, new ManuallySetupExtResourceAction());
    QuickFixAction.registerQuickFixAction(info, new IgnoreExtResourceAction());
  }

  public String getUnresolvedMessagePattern() {
    return XmlErrorMessages.message("uri.is.not.registered");
  }

  public static void processWsdlSchemas(final XmlTag rootTag, Processor<XmlTag> processor) {
    if ("definitions".equals(rootTag.getLocalName())) {
      final XmlTag subTag = rootTag.findFirstSubTag(rootTag.getNamespacePrefix() + ":" + "types");

      if (subTag != null) {
        final XmlTag[] tags = subTag.findSubTags("xsd:schema");
        for(XmlTag t:tags) {
          if (!processor.process(t)) return;
        }
      }
    }
  }
}
