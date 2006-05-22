package com.intellij.xml.util;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;

import java.util.Map;

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
  @NonNls
  public static final String ANCHOR_ELEMENT_NAME = "a";
  private static Key<CachedValue<Map<String,XmlTag>>> ourCachedIdsKey = Key.create("cached.ids");

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
    Map<String,XmlTag> map = getIdMap();
    final XmlTag tag = map != null ? map.get(myAnchor):null;
    if (tag != null) {
      XmlAttribute attribute = tag.getAttribute("id", null);
      if (attribute==null) attribute = tag.getAttribute("name",null);
      return attribute.getValueElement();
    }

    return null;
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

  private Map<String,XmlTag> getIdMap() {
    final XmlFile file = getFile();

    if (file != null) {
      CachedValue<Map<String, XmlTag>> value = file.getUserData(ourCachedIdsKey);
      if (value == null) {
        value = file.getManager().getCachedValuesManager().createCachedValue(new CachedValueProvider<Map<String, XmlTag>>() {
          public Result<Map<String, XmlTag>> compute() {
            final Map<String,XmlTag> resultMap = new HashMap<String, XmlTag>();
            processXmlElements(
              HtmlUtil.getRealXmlDocument(file.getDocument()).getRootTag(),
              new PsiElementProcessor() {
                public boolean execute(final PsiElement element) {
                  final String anchorValue = element instanceof XmlTag ? getAnchorValue((XmlTag)element):null;

                  if (anchorValue!=null) {
                    resultMap.put(anchorValue, (XmlTag)element);
                  }
                  return true;
                }
              }
            );
            return new Result<Map<String, XmlTag>>(resultMap, file);
          }
        }, false);
        file.putUserData(ourCachedIdsKey, value);
      }

      return value.getValue();
    }
    return null;
  }

  private static String getAnchorValue(final XmlTag xmlTag) {
    final String attributeValue = xmlTag.getAttributeValue("id");

    if (attributeValue!=null) {
      return attributeValue;
    }

    if (ANCHOR_ELEMENT_NAME.equalsIgnoreCase(xmlTag.getName())) {
      final String attributeValue2 = xmlTag.getAttributeValue("name");
      if (attributeValue2!=null) {
        return attributeValue2;
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
    final Map<String, XmlTag> idMap = getIdMap();
    if (idMap == null) return ArrayUtil.EMPTY_OBJECT_ARRAY;

    return idMap.keySet().toArray(new Object[idMap.size()]);
  }

  private XmlFile getFile() {
    if (myFileReference!=null) {
      final PsiElement psiElement = myFileReference.resolve();
      return psiElement instanceof XmlFile ? (XmlFile)psiElement:null;
    }

    final PsiFile containingFile = myElement.getContainingFile();
    return containingFile instanceof XmlFile ? (XmlFile)containingFile: PsiUtil.getJspFile(containingFile);
  }

  public boolean isSoft() {
    return myFileReference != null && getFile() != myElement.getContainingFile();
  }
}
