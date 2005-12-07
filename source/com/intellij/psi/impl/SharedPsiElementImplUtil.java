package com.intellij.psi.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference;
import com.intellij.psi.impl.source.tree.ChangeUtil;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class SharedPsiElementImplUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.SharedPsiElementImplUtil");

  private SharedPsiElementImplUtil() {}

  @Nullable
  public static PsiReference findReferenceAt(PsiElement thisElement, int offset) {
    if(thisElement == null)
      return null;
    PsiElement element = thisElement.findElementAt(offset);
    if (element == null) return null;
    offset = thisElement.getTextRange().getStartOffset() + offset - element.getTextRange().getStartOffset();

    while(element != null) {
      final PsiReference[] ref = extractReference(offset, element);
      if (ref.length == 1) return ref[0];
      else if(ref.length > 1) return new PsiMultiReference(ref, element);

      offset = element.getStartOffsetInParent() + offset;
      element = element.getParent();
    }

    return null;
  }

  private static PsiReference[] extractReference(int offset, PsiElement element) {
    final List<PsiReference> referencesList = new ArrayList<PsiReference>();
    int offsetInElement = offset;


    final PsiReference[] references = element.getReferences();
    LOG.assertTrue(references != null, element.toString());
    for (final PsiReference reference : references) {
      if (reference == null) {
        LOG.error(element.toString());
      }
      final TextRange range = reference.getRangeInElement();
      if (range.getStartOffset() <= offsetInElement &&
          (offsetInElement < range.getEndOffset() || (offsetInElement == range.getEndOffset() && range.getLength() == 0))) {
        referencesList.add(reference);
      }
    }

    return referencesList.toArray(new PsiReference[referencesList.size()]);
  }

  @NotNull public static PsiReference[] getReferences(PsiElement thisElement) {
    PsiReference ref = thisElement.getReference();
    if (ref == null) return PsiReference.EMPTY_ARRAY;
    return new PsiReference[] {ref};
  }

  @Nullable
  public static PsiElement getNextSibling(PsiElement element) {
    PsiElement parent = element.getParent();
    if (parent == null) return null;
    PsiElement[] children = parent.getChildren();
    for(int i = 0; i < children.length; i++){
      PsiElement child = children[i];
      if (child.equals(element)) {
        return i < children.length - 1 ? children[i + 1] : null;
      }
    }
    LOG.assertTrue(false);
    return null;
  }

  @Nullable
  public static PsiElement getPrevSibling(PsiElement element) {
    PsiElement parent = element.getParent();
    if (parent == null) return null;
    PsiElement[] children = parent.getChildren();
    for(int i = 0; i < children.length; i++){
      PsiElement child = children[i];
      if (child.equals(element)) {
        return i > 0 ? children[i - 1] : null;
      }
    }
    LOG.assertTrue(false);
    return null;
  }

  public static PsiElement setName(PsiElement element, String name) throws IncorrectOperationException{
    PsiManager manager = element.getManager();
    PsiElementFactory factory = manager.getElementFactory();
    PsiIdentifier newNameIdentifier = factory.createIdentifier(name);
    return element.replace(newNameIdentifier);
  }


  //Hack, but no better idea comes to my mind
  public static TreeElement findFirstChildAfterAddition(TreeElement firstAdded, final TreeElement toFind) {
    final IElementType elementType = toFind.getElementType();
    TreeElement run = firstAdded;
    while(run != null) {
      if (run.getElementType() == elementType) return run;
      run = run.getTreeNext();
    }

    LOG.assertTrue(false, "Could not find element of added class");
    return null;
  }
}
