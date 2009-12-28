/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.LocalQuickFixProvider;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.lang.xhtml.XHTMLLanguage;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.impl.source.xml.XmlEntityRefImpl;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.psi.xml.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ProcessingContext;
import com.intellij.xml.XmlBundle;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.impl.dtd.XmlNSDescriptorImpl;
import com.intellij.xml.util.CheckDtdReferencesInspection;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Maxim.Mossienko
 */
public class DtdReferencesProvider extends PsiReferenceProvider {
  static class ElementReference implements PsiReference, LocalQuickFixProvider, EmptyResolveMessageProvider {
    private final XmlElement myElement;
    private XmlElement myNameElement;
    private final TextRange myRange;
    @NonNls private static final String ELEMENT_DECLARATION_NAME = "ELEMENT";

    public ElementReference(final XmlElement element, final XmlElement nameElement) {
      myElement = element;
      myNameElement = nameElement;

      final int textOffset = element.getTextRange().getStartOffset();
      final int nameTextOffset = nameElement.getTextOffset();

      myRange = new TextRange(
        nameTextOffset - textOffset,
        nameTextOffset + nameElement.getTextLength() - textOffset
      );

    }

    public PsiElement getElement() {
      return myElement;
    }

    public TextRange getRangeInElement() {
      return myRange;
    }

    @Nullable
    public PsiElement resolve() {
      XmlNSDescriptor rootTagNSDescriptor = getNsDescriptor();

      if (rootTagNSDescriptor instanceof XmlNSDescriptorImpl) {
        final XmlElementDescriptor elementDescriptor = ((XmlNSDescriptorImpl)rootTagNSDescriptor).getElementDescriptor(getCanonicalText());

        if (elementDescriptor != null) return elementDescriptor.getDeclaration();
      }
      return null;
    }

    private XmlNSDescriptor getNsDescriptor() {
      final XmlElement parentThatProvidesMetaData = PsiTreeUtil.getParentOfType(
        PsiUtilBase.getOriginalElement(myElement,(Class<XmlElement>)myElement.getClass()),
        XmlDocument.class,
        XmlMarkupDecl.class
      );

      if (parentThatProvidesMetaData instanceof XmlDocument) {
        final XmlDocument document = (XmlDocument)parentThatProvidesMetaData;
        XmlNSDescriptor rootTagNSDescriptor = document.getRootTagNSDescriptor();
        if (rootTagNSDescriptor == null) rootTagNSDescriptor = (XmlNSDescriptor)document.getMetaData();
        return rootTagNSDescriptor;
      } else if (parentThatProvidesMetaData instanceof XmlMarkupDecl) {
        final XmlMarkupDecl markupDecl = (XmlMarkupDecl)parentThatProvidesMetaData;
        final PsiMetaData psiMetaData = markupDecl.getMetaData();

        if (psiMetaData instanceof XmlNSDescriptor) {
          return (XmlNSDescriptor)psiMetaData;
        }
      }

      return null;
    }

    public String getCanonicalText() {
      final XmlElement nameElement = myNameElement;
      return nameElement != null ? nameElement.getText() : "";
    }

    public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
      myNameElement = ElementManipulators.getManipulator(myNameElement).handleContentChange(
        myNameElement,
        new TextRange(0,myNameElement.getTextLength()),
        newElementName
      );

      return null;
    }

    public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
      return null;
    }

    public boolean isReferenceTo(PsiElement element) {
      return myElement.getManager().areElementsEquivalent(element, resolve());
    }

    public Object[] getVariants() {
      final XmlNSDescriptor rootTagNSDescriptor = getNsDescriptor();
      return rootTagNSDescriptor != null ?
             rootTagNSDescriptor.getRootElementsDescriptors(((XmlFile)getRealFile()).getDocument()):
             ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    private PsiFile getRealFile() {
      PsiFile psiFile = myElement.getContainingFile();
      if (psiFile != null) psiFile = psiFile.getOriginalFile();
      return psiFile;
    }

    public boolean isSoft() {
      return true;
    }

    public LocalQuickFix[] getQuickFixes() {
      if (!canHaveAdequateFix(getElement())) return LocalQuickFix.EMPTY_ARRAY;

      return new LocalQuickFix[] {
        new CheckDtdReferencesInspection.AddDtdDeclarationFix(
          "xml.dtd.create.dtd.element.intention.name",
          ELEMENT_DECLARATION_NAME,
          this
        )
      };
    }

    public String getUnresolvedMessagePattern() {
      return XmlBundle.message("xml.dtd.unresolved.element.reference", getCanonicalText());
    }
  }

  static class EntityReference implements PsiReference,LocalQuickFixProvider, EmptyResolveMessageProvider {
    private final PsiElement myElement;
    private final TextRange myRange;
    @NonNls private static final String ENTITY_DECLARATION_NAME = "ENTITY";

    EntityReference(PsiElement element) {
      myElement = element;
      if (element instanceof XmlEntityRef) {
        final PsiElement child = element.getLastChild();
        final int startOffsetInParent = child.getStartOffsetInParent();
        myRange = new TextRange(startOffsetInParent + 1, startOffsetInParent + child.getTextLength() - 1);
      } else {
        myRange = new TextRange(1,myElement.getTextLength()-1);
      }
    }

    public PsiElement getElement() {
      return myElement;
    }

    public TextRange getRangeInElement() {
      return myRange;
    }

    @Nullable
    public PsiElement resolve() {
      XmlEntityDecl xmlEntityDecl = XmlEntityRefImpl.resolveEntity(
        (XmlElement)myElement,
        (myElement instanceof  XmlEntityRef ? myElement.getLastChild():myElement).getText(),
        myElement.getContainingFile()
      );

      if (xmlEntityDecl != null && !xmlEntityDecl.isPhysical()) {
        PsiNamedElement element = XmlUtil.findRealNamedElement(xmlEntityDecl);
        if (element != null) xmlEntityDecl = (XmlEntityDecl)element;
      }
      return xmlEntityDecl;
    }

    public String getCanonicalText() {
      return myRange.substring(myElement.getText());
    }

    public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
      final PsiElement elementAt = myElement.findElementAt(myRange.getStartOffset());
      return ElementManipulators.getManipulator(elementAt).handleContentChange(elementAt, getRangeInElement(), newElementName);
    }

    public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
      return null;
    }

    public boolean isReferenceTo(PsiElement element) {
      return myElement.getManager().areElementsEquivalent(resolve(), element);
    }

    public Object[] getVariants() {
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    public boolean isSoft() {
      return false;
    }

    public LocalQuickFix[] getQuickFixes() {
      if (!canHaveAdequateFix(getElement())) return LocalQuickFix.EMPTY_ARRAY;

      return new LocalQuickFix[] {
        new CheckDtdReferencesInspection.AddDtdDeclarationFix(
          "xml.dtd.create.entity.intention.name",
          myElement.getText().charAt(myRange.getStartOffset() - 1) == '%' ?
          ENTITY_DECLARATION_NAME + " %":
          ENTITY_DECLARATION_NAME,
          this
        )
      };
    }

    public String getUnresolvedMessagePattern() {
      return XmlBundle.message("xml.dtd.unresolved.entity.reference", getCanonicalText());
    }
  }

  private static boolean canHaveAdequateFix(PsiElement element) {
    final PsiFile containingFile = element.getContainingFile();

    if (containingFile.getLanguage() == HTMLLanguage.INSTANCE ||
        containingFile.getLanguage() == XHTMLLanguage.INSTANCE ||
        containingFile.getViewProvider() instanceof TemplateLanguageFileViewProvider
      ) {
      return false;
    }
    return true;
  }

  @NotNull
  public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull final ProcessingContext context) {
    XmlElement nameElement = null;

    if (element instanceof XmlDoctype) {
      nameElement = ((XmlDoctype)element).getNameElement();
    } else if (element instanceof XmlElementDecl) {
      nameElement = ((XmlElementDecl)element).getNameElement();
    } else if (element instanceof XmlAttlistDecl) {
      nameElement = ((XmlAttlistDecl)element).getNameElement();
    } else if (element instanceof XmlElementContentSpec) {
      final PsiElement[] children = element.getChildren();
      final List<PsiReference> psiRefs = new ArrayList<PsiReference>(children.length);

      for (final PsiElement child : children) {
        if (child instanceof XmlToken && ((XmlToken)child).getTokenType() == XmlTokenType.XML_NAME) {
          psiRefs.add(new ElementReference((XmlElement)element, (XmlElement)child));
        }
      }
      
      return psiRefs.toArray(new PsiReference[psiRefs.size()]);
    }

    if (nameElement != null) {
      return new PsiReference[] { new ElementReference((XmlElement)element, nameElement) };
    }

    if (element instanceof XmlEntityRef ||
        (element instanceof XmlToken && ((XmlToken)element).getTokenType() == XmlTokenType.XML_CHAR_ENTITY_REF)) {
      return new PsiReference[] { new EntityReference(element) };
    }

    return PsiReference.EMPTY_ARRAY;
  }

  public ElementFilter getSystemReferenceFilter() {
    return new ElementFilter() {
      public boolean isAcceptable(Object element, PsiElement context) {
        final PsiElement parent = context.getParent();
        
        if((parent instanceof XmlEntityDecl &&
           !((XmlEntityDecl)parent).isInternalReference()
           )
          ) {
          PsiElement prevSibling = context.getPrevSibling();
          if (prevSibling instanceof PsiWhiteSpace) {
            prevSibling = prevSibling.getPrevSibling();
          }

          if (prevSibling instanceof XmlToken &&
              ((XmlToken)prevSibling).getTokenType() == XmlTokenType.XML_DOCTYPE_SYSTEM ||
              prevSibling instanceof XmlAttributeValue
            ) {
            return true;
          }
        }

        return false;
      }

      public boolean isClassAcceptable(Class hintClass) {
        return true;
      }
    };
  }
}
