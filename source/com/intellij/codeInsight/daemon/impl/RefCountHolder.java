
package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.BidirectionalMap;

import java.util.*;

public class RefCountHolder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.RefCountHolder");

  private final PsiFile myFile;

  private BidirectionalMap<PsiElement,PsiElement> myLocalRefsMap = new BidirectionalMap<PsiElement, PsiElement>();

  private HashMap<PsiNamedElement,Boolean> myDclsUsedMap = new HashMap<PsiNamedElement,Boolean>();
  private Map<PsiElement, PsiImportStatementBase> myImportStatements = new HashMap<PsiElement, PsiImportStatementBase>();

  public RefCountHolder(PsiFile file) {
    myFile = file;
  }

  public void clear() {
    myLocalRefsMap.clear();
    myImportStatements.clear();
  }

  public void registerLocallyReferenced(PsiNamedElement result) {
    myDclsUsedMap.put(result,Boolean.TRUE);
  }

  public void registerLocalDcl(PsiNamedElement dcl) {
    myDclsUsedMap.put(dcl,Boolean.FALSE);
  }

  public void registerReference(PsiElement ref, ResolveResult resolveResult) {
    PsiElement refElement = resolveResult.getElement();
    if (refElement != null && getFile().equals(refElement.getContainingFile())) {
      registerLocalRef(ref, refElement);
    }

    PsiElement resolveScope = resolveResult.getCurrentFileResolveScope();
    if (resolveScope instanceof PsiImportStatementBase) {
      registerImportStatement(ref, (PsiImportStatementBase)resolveScope);
    }
  }

  private void registerImportStatement (PsiElement ref, PsiImportStatementBase importStatement) {
    myImportStatements.put(ref, importStatement);
  }

  public boolean isRedundant(PsiImportStatementBase importStatement) {
    return !myImportStatements.values().contains(importStatement);
  }

  private void registerLocalRef(PsiElement ref, PsiElement refElement) {
    if (refElement instanceof PsiMethod && PsiTreeUtil.isAncestor(refElement, ref, true)) return; // filter self-recursive calls
    if (refElement instanceof PsiClass && PsiTreeUtil.isAncestor(refElement, ref, true)) return; // filter inner use of itself
    myLocalRefsMap.put(ref, refElement);
  }

  public void removeInvalidRefs() {
    for(Iterator iterator = myLocalRefsMap.keySet().iterator(); iterator.hasNext();){
      PsiElement ref = (PsiElement)iterator.next();
      if (!ref.isValid()){
        PsiElement value = myLocalRefsMap.get(ref);
        iterator.remove();
        List<PsiElement> array = myLocalRefsMap.getKeysByValue(value);
        array.remove(ref);
      }
    }
    for (Iterator iterator = myImportStatements.keySet().iterator(); iterator.hasNext();) {
      PsiElement ref = (PsiElement)iterator.next();
      if (!ref.isValid()) iterator.remove();
    }
  }

  public int getRefCount(PsiElement element) {
    List array = myLocalRefsMap.getKeysByValue(element);
    if(array != null) return array.size();

    Boolean usedStatus = myDclsUsedMap.get(element);
    if (usedStatus == Boolean.TRUE) return 1;

    return 0;
  }

  public int getReadRefCount(PsiElement element) {
    LOG.assertTrue(element instanceof PsiVariable);
    List array = myLocalRefsMap.getKeysByValue(element);
    if (array == null) return 0;
    int count = 0;
    for(int i = 0; i < array.size(); i++){
      PsiElement ref = (PsiElement)array.get(i);
      if (!(ref instanceof PsiExpression)){ // possible with uncomplete code
        count++;
        continue;
      }
      if (PsiUtil.isAccessedForReading((PsiExpression)ref)){
        if (ref.getParent() instanceof PsiExpression &&
            ref.getParent().getParent() instanceof PsiExpressionStatement &&
            PsiUtil.isAccessedForWriting((PsiExpression)ref)){
          continue; // "var++;"
        }
        count++;
      }
    }
    return count;
  }

  public int getWriteRefCount(PsiElement element) {
    LOG.assertTrue(element instanceof PsiVariable);
    List array = myLocalRefsMap.getKeysByValue(element);
    if (array == null) return 0;
    int count = 0;
    for(int i = 0; i < array.size(); i++){
      PsiElement ref = (PsiElement)array.get(i);
      if (!(ref instanceof PsiExpression)){ // possible with uncomplete code
        count++;
        continue;
      }
      if (PsiUtil.isAccessedForWriting((PsiExpression)ref)){
        count++;
      }
    }
    return count;
  }

  public PsiFile getFile() {
    return myFile;
  }

  public PsiNamedElement[] getUnusedDcls() {
    final List<PsiNamedElement> result = new LinkedList<PsiNamedElement>();
    final Set<Map.Entry<PsiNamedElement, Boolean>> entries = myDclsUsedMap.entrySet();

    for (Iterator<Map.Entry<PsiNamedElement, Boolean>> iterator = entries.iterator(); iterator.hasNext();) {
      final Map.Entry<PsiNamedElement, Boolean> entry = iterator.next();

      if (entry.getValue() == Boolean.FALSE) result.add(entry.getKey());
    }

    return result.toArray(new PsiNamedElement[result.size()]);
  }
}
