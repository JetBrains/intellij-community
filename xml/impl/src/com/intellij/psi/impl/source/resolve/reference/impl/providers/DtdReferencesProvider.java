package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.LocalQuickFixProvider;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.impl.source.xml.XmlEntityRefImpl;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
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
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Jul 4, 2005
 * Time: 3:58:53 PM
 * To change this template use File | Settings | File Templates.
 */
public class DtdReferencesProvider implements PsiReferenceProvider {
  static class ElementReference implements PsiReference, LocalQuickFixProvider, EmptyResolveMessageProvider {
    private XmlElement myElement;
    private XmlElement myNameElement;
    private TextRange myRange;
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
        PsiUtil.getOriginalElement(myElement,(Class<XmlElement>)myElement.getClass()),
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
      myNameElement = ReferenceProvidersRegistry.getInstance(myElement.getProject()).getManipulator(myNameElement).handleContentChange(
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
      if (psiFile != null && psiFile.getOriginalFile() != null) psiFile = psiFile.getOriginalFile();
      return psiFile;
    }

    public boolean isSoft() {
      return true;
    }

    public LocalQuickFix[] getQuickFixes() {
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
    private PsiElement myElement;
    private TextRange myRange;
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
        myElement.getLastChild().getText(),
        myElement.getContainingFile()
      );

      if (xmlEntityDecl != null && !xmlEntityDecl.isPhysical()) {
        PsiNamedElement element = XmlUtil.findRealNamedElement(xmlEntityDecl);
        if (element != null) xmlEntityDecl = (XmlEntityDecl)element;
      }
      return xmlEntityDecl;
    }

    public String getCanonicalText() {
      return myElement.getText().substring(myRange.getStartOffset(),myRange.getEndOffset());
    }

    public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
      final PsiElement elementAt = myElement.findElementAt(myRange.getStartOffset());
      return ReferenceProvidersRegistry.getInstance(myElement.getProject()).getManipulator(
        elementAt
      ).handleContentChange(elementAt, getRangeInElement(), newElementName);
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

  @NotNull
  public PsiReference[] getReferencesByElement(PsiElement element) {
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
