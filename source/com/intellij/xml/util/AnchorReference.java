package com.intellij.xml.util;

import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.IncorrectOperationException;

import java.util.List;
import java.util.ArrayList;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Apr 15, 2005
 * Time: 6:01:35 PM
 * To change this template use File | Settings | File Templates.
 */
class AnchorReference implements PsiReference {
  private String myAnchor;
  private PsiReference myFileReference;
  private PsiElement myElement;
  private int myOffset;

  AnchorReference(final String anchor, final PsiReference psiReference, final PsiElement element) {
    myAnchor = anchor;
    myFileReference = psiReference;
    myElement = element;
    myOffset = element.getText().indexOf(anchor);
  }

  public PsiElement getElement() {
    return myElement;
  }

  public TextRange getRangeInElement() {
    return new TextRange(myOffset,myOffset+myAnchor.length());
  }

  public PsiElement resolve(){
    final PsiManager manager = getElement().getManager();

    if(manager instanceof PsiManagerImpl){
      return ((PsiManagerImpl)manager).getResolveCache().resolveWithCaching(this, new ResolveCache.Resolver() {
        public PsiElement resolve(PsiReference ref, boolean incompleteCode) {
          return resolveInner();
        }
      }, false, false);
    }
    return resolveInner();
  }

  static boolean processXmlElements(XmlTag element, PsiElementProcessor processor) {
    if (!_processXmlElements(element,processor)) return false;
    
    for(PsiElement next = element.getNextSibling(); next != null; next = next.getNextSibling()) {
      if (next instanceof XmlTag) {
        if (!_processXmlElements((XmlTag)next,processor)) return false;
      }
    }

    return true;
  }
  
  static boolean _processXmlElements(XmlTag element, PsiElementProcessor processor) {
    if (!processor.execute(element)) return false;
    final XmlTag[] subTags = element.getSubTags();

    for (int i = 0; i < subTags.length; i++) {
      if(!_processXmlElements(subTags[i],processor)) return false;
    }

    return true;
  }

  private PsiElement resolveInner() {
    final PsiElement[] result = new PsiElement[1];

    final XmlFile file = getFile();
    if (file != null) {
      processXmlElements(
        HtmlUtil.getRealXmlDocument(file.getDocument()).getRootTag(),
        new PsiElementProcessor() {
          public boolean execute(final PsiElement element) {
            final String anchorValue = getAnchorValue(element);

            if (anchorValue!=null && anchorValue.equals(myAnchor)) {
              final XmlTag xmlTag = (XmlTag)element;
              XmlAttribute attribute = xmlTag.getAttribute("id", null);
              if (attribute==null) attribute = xmlTag.getAttribute("name",null);
              result[0] = attribute.getValueElement();
              return false;
            }
            return true;
          }
        }
      );
    }

    return result[0];
  }

  private String getAnchorValue(final PsiElement element) {
    if (element instanceof XmlTag) {
      final XmlTag xmlTag = ((XmlTag)element);
      final String attributeValue = xmlTag.getAttributeValue("id");

      if (attributeValue!=null) {
        return attributeValue;
      }

      if ("a".equalsIgnoreCase(xmlTag.getName())) {
        final String attributeValue2 = xmlTag.getAttributeValue("name");
        if (attributeValue2!=null) {
          return attributeValue2;
        }
      }
    }

    return null;
  }

  public String getCanonicalText() {
    return myAnchor;
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    return ReferenceProvidersRegistry.getInstance(myElement.getProject()).getManipulator(myElement).handleContentChange(
      myElement,
      getRangeInElement(),
      newElementName
    );
  }

  public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException {
    return null;
  }

  public boolean isReferenceTo(PsiElement element) {
    return myElement.getManager().areElementsEquivalent(element,resolve());
  }

  public Object[] getVariants() {
    final List<String> variants = new ArrayList<String>(3);

    final XmlFile file = getFile();
    if (file!=null) {
      processXmlElements(
        HtmlUtil.getRealXmlDocument(file.getDocument()).getRootTag(),
        new PsiElementProcessor() {
          public boolean execute(final PsiElement element) {
            final String anchorValue = getAnchorValue(element);

            if (anchorValue!=null) {
              variants.add(anchorValue);
            }
            return true;
          }
        }
      );
    }

    return variants.toArray(new String[variants.size()]);
  }

  private XmlFile getFile() {
    if (myFileReference!=null) {
      final PsiElement psiElement = myFileReference.resolve();
      return psiElement instanceof XmlFile ? (XmlFile)psiElement:null;
    }

    final PsiFile containingFile = myElement.getContainingFile();
    return containingFile instanceof XmlFile ? (XmlFile)containingFile:null;
  }

  public boolean isSoft() {
    return myFileReference != null && getFile() != myElement.getContainingFile();
  }
}
