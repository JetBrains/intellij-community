package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.psi.impl.source.resolve.reference.PsiReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.xml.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Jul 4, 2005
 * Time: 3:58:53 PM
 * To change this template use File | Settings | File Templates.
 */
public class DtdReferencesProvider implements PsiReferenceProvider {
  static class EntityReference implements PsiReference {
    private PsiElement myElement;
    
    EntityReference(PsiElement element) {
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
      return ((XmlEntityRef)myElement).resolve(myElement.getContainingFile());  
    }

    public String getCanonicalText() {
      return myElement.getText();
    }

    public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
      return null;
    }

    public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException {
      return null;
    }

    public boolean isReferenceTo(PsiElement element) {
      return myElement.getManager().areElementsEquivalent(resolve(), element);
    }

    public Object[] getVariants() {
      return new Object[0];
    }

    public boolean isSoft() {
      return true;
    }
  }
  
  public PsiReference[] getReferencesByElement(PsiElement element) {
    if (element instanceof XmlEntityRef)
      return new PsiReference[] { new EntityReference(element) };
    
    return PsiReference.EMPTY_ARRAY;
  }

  public PsiReference[] getReferencesByElement(PsiElement element, ReferenceType type) {
    return PsiReference.EMPTY_ARRAY;
  }

  public PsiReference[] getReferencesByString(String str, PsiElement position, ReferenceType type, int offsetInPosition) {
    return PsiReference.EMPTY_ARRAY;
  }

  public void handleEmptyContext(PsiScopeProcessor processor, PsiElement position) {
  }
  
  public ElementFilter getSystemReferenceFilter() {
    return new ElementFilter() {
      public boolean isAcceptable(Object element, PsiElement context) {
        final PsiElement parent = context.getParent();
        if(parent instanceof XmlEntityDecl &&
           !((XmlEntityDecl)parent).isInternalReference()
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
