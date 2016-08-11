/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider;
import com.intellij.psi.xml.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ProcessingContext;
import com.intellij.xml.XmlBundle;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.util.AddDtdDeclarationFix;
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

    @Override
    public PsiElement getElement() {
      return myElement;
    }

    @Override
    public TextRange getRangeInElement() {
      return myRange;
    }

    @Override
    @Nullable
    public PsiElement resolve() {
      XmlElementDescriptor descriptor = DtdResolveUtil.resolveElementReference(getCanonicalText(), myElement);
      return descriptor == null ? null : descriptor.getDeclaration();
    }


    @Override
    @NotNull
    public String getCanonicalText() {
      final XmlElement nameElement = myNameElement;
      return nameElement != null ? nameElement.getText() : "";
    }

    @Override
    public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
      myNameElement = ElementManipulators.getManipulator(myNameElement).handleContentChange(
        myNameElement,
        new TextRange(0,myNameElement.getTextLength()),
        newElementName
      );

      return null;
    }

    @Override
    public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
      return null;
    }

    @Override
    public boolean isReferenceTo(PsiElement element) {
      return myElement.getManager().areElementsEquivalent(element, resolve());
    }

    @Override
    @NotNull
    public Object[] getVariants() {
      final XmlNSDescriptor rootTagNSDescriptor = DtdResolveUtil.getNsDescriptor(myElement);
      return rootTagNSDescriptor != null ?
             rootTagNSDescriptor.getRootElementsDescriptors(((XmlFile)getRealFile()).getDocument()):
             ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    private PsiFile getRealFile() {
      PsiFile psiFile = myElement.getContainingFile();
      if (psiFile != null) psiFile = psiFile.getOriginalFile();
      return psiFile;
    }

    @Override
    public boolean isSoft() {
      return true;
    }

    @Override
    public LocalQuickFix[] getQuickFixes() {
      if (!canHaveAdequateFix(getElement())) return LocalQuickFix.EMPTY_ARRAY;

      return new LocalQuickFix[] {
        new AddDtdDeclarationFix(
          "xml.dtd.create.dtd.element.intention.name",
          ELEMENT_DECLARATION_NAME,
          this
        )
      };
    }

    @Override
    @NotNull
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

    @Override
    public PsiElement getElement() {
      return myElement;
    }

    @Override
    public TextRange getRangeInElement() {
      return myRange;
    }

    @Override
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

    @Override
    @NotNull
    public String getCanonicalText() {
      return myRange.substring(myElement.getText());
    }

    @Override
    public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
      final PsiElement elementAt = myElement.findElementAt(myRange.getStartOffset());
      return ElementManipulators.getManipulator(elementAt).handleContentChange(elementAt, getRangeInElement(), newElementName);
    }

    @Override
    public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
      return null;
    }

    @Override
    public boolean isReferenceTo(PsiElement element) {
      return myElement.getManager().areElementsEquivalent(resolve(), element);
    }

    @Override
    @NotNull
    public Object[] getVariants() {
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    @Override
    public boolean isSoft() {
      return false;
    }

    @Override
    public LocalQuickFix[] getQuickFixes() {
      if (!canHaveAdequateFix(getElement())) return LocalQuickFix.EMPTY_ARRAY;

      return new LocalQuickFix[] {
        new AddDtdDeclarationFix(
          "xml.dtd.create.entity.intention.name",
          myElement.getText().charAt(myRange.getStartOffset() - 1) == '%' ?
          ENTITY_DECLARATION_NAME + " %":
          ENTITY_DECLARATION_NAME,
          this
        )
      };
    }

    @Override
    @NotNull
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

  @Override
  @NotNull
  public PsiReference[] getReferencesByElement(@NotNull final PsiElement element, @NotNull final ProcessingContext context) {
    XmlElement nameElement = null;

    if (element instanceof XmlDoctype) {
      nameElement = ((XmlDoctype)element).getNameElement();
    } else if (element instanceof XmlElementDecl) {
      nameElement = ((XmlElementDecl)element).getNameElement();
    } else if (element instanceof XmlAttlistDecl) {
      nameElement = ((XmlAttlistDecl)element).getNameElement();
    }
    else if (element instanceof XmlElementContentSpec) {
      final List<PsiReference> psiRefs = new ArrayList<>();
      element.accept(new PsiRecursiveElementVisitor() {
        @Override
        public void visitElement(PsiElement child) {
          if (child instanceof XmlToken && ((XmlToken)child).getTokenType() == XmlTokenType.XML_NAME) {
            psiRefs.add(new ElementReference((XmlElement)element, (XmlElement)child));
          }
          super.visitElement(child);
        }
      });
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
      @Override
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

      @Override
      public boolean isClassAcceptable(Class hintClass) {
        return true;
      }
    };
  }
}
