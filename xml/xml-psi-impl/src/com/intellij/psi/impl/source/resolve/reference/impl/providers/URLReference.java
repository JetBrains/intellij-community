// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.javaee.ExternalResourceManager;
import com.intellij.javaee.ExternalResourceManagerEx;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.psi.XmlPsiBundle;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
*/
public class URLReference implements PsiReference, EmptyResolveMessageProvider {
  @NonNls public static final String TARGET_NAMESPACE_ATTR_NAME = "targetNamespace";

  private final PsiElement myElement;
  private final TextRange myRange;
  private final boolean mySoft;
  private boolean myIncorrectResourceMapped;

  public URLReference(PsiElement element) {
    this(element, null, false);
  }

  public URLReference(PsiElement element, @Nullable TextRange range, boolean soft) {
    myElement = element;
    myRange = range;
    mySoft = soft;
  }

  @NotNull
  @Override
  public PsiElement getElement() {
    return myElement;
  }

  @NotNull
  @Override
  public TextRange getRangeInElement() {
    return myRange != null ? myRange : ElementManipulators.getValueTextRange(myElement);
  }

  @Override
  @Nullable
  public PsiElement resolve() {
    myIncorrectResourceMapped = false;
    final String canonicalText = getCanonicalText();

    if (canonicalText.isEmpty()) {
      final XmlAttribute attr = PsiTreeUtil.getParentOfType(getElement(), XmlAttribute.class);

      if (attr != null &&
            attr.isNamespaceDeclaration() &&
            attr.getNamespacePrefix().isEmpty() ||
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
        PsiFile file = XmlUtil.findRelativeFile(canonicalText, myElement.getContainingFile());
        if (file == null) {
          myIncorrectResourceMapped = true;
        }
        return file;
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
      processWsdlSchemas(rootTag, t -> {
        if (canonicalText.equals(t.getAttributeValue(TARGET_NAMESPACE_ATTR_NAME))) {
          result[0] = t;
          return false;
        }
        for (XmlTag anImport : t.findSubTags("import", t.getNamespace())) {
          if (canonicalText.equals(anImport.getAttributeValue("namespace"))) {
            final XmlAttribute location = anImport.getAttribute("schemaLocation");
            if (location != null) {
              result[0] = FileReferenceUtil.findFile(location.getValueElement());
            }
          }
        }
        return true;
      });

      return result[0];
    }
    return null;
  }

  @Override
  @NotNull
  public String getCanonicalText() {
    final String text = myElement.getText();
    if (text.length() > 1) {
      return myRange == null ? text.substring(1, text.length() - 1) : myRange.substring(text);
    }

    return "";
  }

  @Override
  public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
    final TextRange textRangeInElement = getRangeInElement();
    final PsiElement elementToChange = myElement.findElementAt(textRangeInElement.getStartOffset());
    assert elementToChange != null;
    final int offsetFromStart = myElement.getTextRange().getStartOffset() + textRangeInElement.getStartOffset() - elementToChange.getTextOffset();

    ElementManipulators.handleContentChange(elementToChange, new TextRange(offsetFromStart, offsetFromStart + textRangeInElement.getLength()),newElementName);
    return myElement;
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    assert element instanceof PsiFile;

    if (!XmlUtil.isUrlText(getCanonicalText(), element.getProject())) {
      // TODO: this should work!
      final VirtualFile virtualFile = ((PsiFile)element).getVirtualFile();
      assert virtualFile != null;
      handleElementRename(VfsUtilCore.fixIDEAUrl(virtualFile.getPresentableUrl()));
    }
    return myElement;
  }

  @Override
  public boolean isReferenceTo(@NotNull PsiElement element) {
    return myElement.getManager().areElementsEquivalent(resolve(),element);
  }

  public boolean isSchemaLocation() { return false; }

  @Override
  public boolean isSoft() {
    return mySoft;
  }

  @Override
  @NotNull
  public String getUnresolvedMessagePattern() {
    return XmlPsiBundle.message(myIncorrectResourceMapped ? "xml.inspections.registered.resource.is.not.recognized"
                                                          : "xml.inspections.uri.is.not.registered");
  }

  public static void processWsdlSchemas(final XmlTag rootTag, Processor<? super XmlTag> processor) {
    if ("definitions".equals(rootTag.getLocalName())) {
      final String nsPrefix = rootTag.getNamespacePrefix();
      final String types = nsPrefix.isEmpty() ? "types" : nsPrefix  + ":types";
      final XmlTag subTag = rootTag.findFirstSubTag(types);

      if (subTag != null) {
        for (int i = 0; i < XmlUtil.SCHEMA_URIS.length; i++) {
          final XmlTag[] tags = subTag.findSubTags("schema", XmlUtil.SCHEMA_URIS[i]);
          for (XmlTag t : tags) {
            if (!processor.process(t)) return;
          }
        }
      }
    }
  }
}
