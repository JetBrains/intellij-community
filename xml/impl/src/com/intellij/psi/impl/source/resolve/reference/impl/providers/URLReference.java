/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.codeInsight.daemon.QuickFixProvider;
import com.intellij.codeInsight.daemon.XmlErrorMessages;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.quickfix.*;
import com.intellij.javaee.ExternalResourceManager;
import com.intellij.javaee.ExternalResourceManagerEx;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.XmlSchemaProvider;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Dmitry Avdeev
*/
public class URLReference implements PsiReference, QuickFixProvider, EmptyResolveMessageProvider {
  @NonNls private static final String TARGET_NAMESPACE_ATTR_NAME = "targetNamespace";

  private final PsiElement myElement;
  private TextRange myRange;
  private boolean mySoft;
  private boolean myIncorrectResourceMapped;

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
    return myRange != null ? myRange : ElementManipulators.getValueTextRange(myElement);
  }

  @Nullable
  public PsiElement resolve() {
    myIncorrectResourceMapped = false;
    final String canonicalText = getCanonicalText();

    if (canonicalText.length() == 0) {
      final XmlAttribute attr = PsiTreeUtil.getParentOfType(getElement(), XmlAttribute.class);

      if (( attr != null &&
            attr.isNamespaceDeclaration() &&
            attr.getNamespacePrefix().length() == 0
          ) ||
          ExternalResourceManagerEx.getInstanceEx().isIgnoredResource(canonicalText)
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

      final String url = ExternalResourceManager.getInstance().getResourceLocation(canonicalText, myElement.getProject());
      if (!url.equals(canonicalText)) {
        myIncorrectResourceMapped = true;
        return null;
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

  @NotNull
  public String getCanonicalText() {
    final String text = myElement.getText();
    if (text.length() > 1) {
      return myRange == null ? text.substring(1, text.length() - 1) : myRange.substring(text);
    }

    return "";
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    final TextRange textRangeInElement = getRangeInElement();
    final PsiElement elementToChange = myElement.findElementAt(textRangeInElement.getStartOffset());
    assert elementToChange != null;
    final ElementManipulator<PsiElement> manipulator = ElementManipulators.getManipulator(elementToChange);
    assert manipulator != null;
    final int offsetFromStart = myElement.getTextRange().getStartOffset() + textRangeInElement.getStartOffset() - elementToChange.getTextOffset();

    manipulator.handleContentChange(elementToChange, new TextRange(offsetFromStart, offsetFromStart + textRangeInElement.getLength()),newElementName);
    return myElement;
  }

  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    assert element instanceof PsiFile;

    if (!URIReferenceProvider.isUrlText(getCanonicalText(), element.getProject())) {
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

  @NotNull
  public Object[] getVariants() {
    final XmlFile file = (XmlFile)myElement.getContainingFile();
    Set<String> list = new HashSet<String>();
    for (XmlSchemaProvider provider : Extensions.getExtensions(XmlSchemaProvider.EP_NAME)) {
      if (provider.isAvailable(file)) {
        list.addAll(provider.getAvailableNamespaces(file, null));
      }
    }
    if (!list.isEmpty()) {
      return ArrayUtil.toObjectArray(list);
    }
    String[] resourceUrls = ExternalResourceManager.getInstance().getResourceUrls(null, true);
    final XmlDocument document = file.getDocument();
    assert document != null;
    XmlTag rootTag = document.getRootTag();
    final ArrayList<String> additionalNs = new ArrayList<String>();
    if (rootTag != null) processWsdlSchemas(rootTag, new Processor<XmlTag>() {
      public boolean process(final XmlTag xmlTag) {
        final String s = xmlTag.getAttributeValue(TARGET_NAMESPACE_ATTR_NAME);
        if (s != null) { additionalNs.add(s); }
        return true;
      }
    });
    resourceUrls = ArrayUtil.mergeArrays(resourceUrls, ArrayUtil.toStringArray(additionalNs), String.class);
    return resourceUrls;
  }

  public boolean isSoft() {
    return mySoft;
  }

  public void registerQuickfix(HighlightInfo info, PsiReference reference) {
    QuickFixAction.registerQuickFixAction(info, new FetchExtResourceAction());
    QuickFixAction.registerQuickFixAction(info, new ManuallySetupExtResourceAction());
    QuickFixAction.registerQuickFixAction(info, new IgnoreExtResourceAction());
    final PsiElement parentElement = reference.getElement().getParent();

    if (parentElement instanceof XmlAttribute && ((XmlAttribute)parentElement).isNamespaceDeclaration()) {
      QuickFixAction.registerQuickFixAction(info, new AddXsiSchemaLocationForExtResourceAction());
    }
  }

  public String getUnresolvedMessagePattern() {
    return XmlErrorMessages.message(myIncorrectResourceMapped ? "registered.resource.is.not.recognized":"uri.is.not.registered");
  }

  public static void processWsdlSchemas(final XmlTag rootTag, Processor<XmlTag> processor) {
    if ("definitions".equals(rootTag.getLocalName())) {
      final String nsPrefix = rootTag.getNamespacePrefix();
      final String types = nsPrefix.length() == 0 ? "types" : nsPrefix  + ":types";
      final XmlTag subTag = rootTag.findFirstSubTag(types);

      if (subTag != null) {
        XmlTag[] tags = subTag.findSubTags("xsd:schema");
        if (tags.length == 0) {
          tags = subTag.findSubTags("schema");
        }
        for(XmlTag t:tags) {
          if (!processor.process(t)) return;
        }
      }
    }
  }
}
