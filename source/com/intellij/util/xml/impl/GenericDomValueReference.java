/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.javaee.J2EEBundle;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.impl.source.resolve.reference.impl.GenericReference;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagValue;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.GenericDomValue;

/**
 * author: lesya
 */
public class GenericDomValueReference extends GenericReference {
  private final GenericDomValue myXmlValue;
  private final XmlElement myElement;
  private final TextRange myTextRange;

  public GenericDomValueReference(final PsiReferenceProvider provider, GenericDomValue xmlValue) {
    this(provider, xmlValue, calculateOffset(xmlValue.getXmlTag()));
  }

  private static TextRange calculateOffset(XmlTag tag) {
    final XmlTagValue tagValue = tag.getValue();
    final String trimmedText = tagValue.getTrimmedText();
    final int inside = tagValue.getText().indexOf(trimmedText);
    final int startOffset = tagValue.getTextRange().getStartOffset() - tag.getTextRange().getStartOffset() + inside;
    return new TextRange(startOffset, startOffset + trimmedText.length());
  }

  public GenericDomValueReference(final PsiReferenceProvider provider, GenericDomValue xmlValue, TextRange textRange) {
    super(provider);
    myXmlValue = xmlValue;
    myTextRange = textRange;
    myElement = xmlValue.getXmlTag();
  }

  public PsiElement getContext() {
    return myElement;
  }

  public PsiReference getContextReference() {
    return null;
  }

  public GenericDomValue getGenericValue() {
    return myXmlValue;
  }

  public ReferenceType getType() {
    return new ReferenceType(ReferenceType.UNKNOWN);
  }

  public PsiElement resolveInner() {
      final Object o = myXmlValue.getValue();
      if (o instanceof PsiClass) {
        return (PsiClass)o;
      }
    return null;
    //todo[peter] resolve reference
/*
    List<XmlDataOwner> definitions = myXmlObjectsManager.resolve(myXmlValue.getReferenceClass(),
                                                                 myValue,
                                                                 myXmlValue.getReferenceScope(), myLinkFile);
    if (definitions.isEmpty()) {
      return null;
    }
    else {
      return ((XmlBasedObjectImpl)(definitions.get(0)).getXmlData()).getXmlTag();
    }*/
  }

  public ReferenceType getSoftenType() {
    return getType();
  }

  public boolean needToCheckAccessibility() {
    return false;
  }

  public PsiElement getElement() {
    return getContext();
  }

  public TextRange getRangeInElement() {
    return myTextRange;
  }

  public String getCanonicalText() {
    String value = myXmlValue.getStringValue();
    if (value != null) {
      return value;
    }
    else {
      return J2EEBundle.message("unknown.j2ee.reference.canonical.text");
    }
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    //try {
      myXmlValue.setStringValue(newElementName);
    /*}
    catch (ReadOnlyDeploymentDescriptorModificationException e) {
      VirtualFileManager.getInstance().fireReadOnlyModificationAttempt(new VirtualFile[]{e.getVirtualFile()});
    }*/
    return myXmlValue.getXmlTag();
  }

  public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException {
    //try {
      if (element instanceof PsiClass) {
        myXmlValue.setStringValue(((PsiClass)element).getName());
        return myXmlValue.getXmlTag();
      }
      else if (element instanceof XmlTag) {
        myXmlValue.setStringValue(((XmlTag)element).getName());
        return myXmlValue.getXmlTag();
      }
      else {
        return null;
      }
    /*}
    catch (ReadOnlyDeploymentDescriptorModificationException e) {
      return null;
    }*/

  }

  public Object[] getVariants() {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
    //todo[peter] variants
    /*if (myXmlValue instanceof ReferenceToPsiElement) {
    }
    else {
      ArrayList<String> result = new ArrayList<String>();

      List<XmlDataOwner> values = myXmlObjectsManager.getObjectsByType(myXmlValue.getReferenceClass(), myXmlValue.getReferenceScope(),
                                                                       myLinkFile);

      for (XmlDataOwner data : values) {
        List<XmlValueOnTag> identicalFields = ((XmlBasedObjectImpl)data.getXmlData()).getIdenticalFields();
        if (!identicalFields.isEmpty()) {
          result.add(identicalFields.get(0).getStringValue());
        }
      }
      return result.toArray(new Object[result.size()]);
    }*/
  }
}
