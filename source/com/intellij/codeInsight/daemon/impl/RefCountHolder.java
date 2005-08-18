
package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiMatcherImpl;
import com.intellij.util.containers.BidirectionalMap;
import com.intellij.util.ArrayUtil;

import java.util.*;

public class RefCountHolder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.RefCountHolder");

  private final PsiFile myFile;

  private final BidirectionalMap<PsiReference,PsiElement> myLocalRefsMap = new BidirectionalMap<PsiReference, PsiElement>();

  private final Map<PsiNamedElement, Boolean> myDclsUsedMap = Collections.synchronizedMap(new HashMap<PsiNamedElement, Boolean>());
  private final Map<String, XmlTag> myXmlId2TagMap = Collections.synchronizedMap(new HashMap<String, XmlTag>());
  private final Map<PsiReference, PsiImportStatementBase> myImportStatements = Collections.synchronizedMap(new HashMap<PsiReference, PsiImportStatementBase>());
  private final Set<PsiNamedElement> myUsedElements = Collections.synchronizedSet(new HashSet<PsiNamedElement>());

  public RefCountHolder(PsiFile file) {
    myFile = file;
  }

  public void clear() {
    myLocalRefsMap.clear();
    myImportStatements.clear();
    myDclsUsedMap.clear();
    myXmlId2TagMap.clear();
    myUsedElements.clear();
  }

  public void registerLocallyReferenced(PsiNamedElement result) {
    myDclsUsedMap.put(result,Boolean.TRUE);
  }

  public void registerLocalDcl(PsiNamedElement dcl) {
    myDclsUsedMap.put(dcl,Boolean.FALSE);
    addStatistics(dcl);
  }

  private static void addStatistics(final PsiNamedElement dcl) {
    final PsiType typeByPsiElement = PsiUtil.getTypeByPsiElement(dcl);
    final StatisticsManager.NameContext context = StatisticsManager.getContext(dcl);
    if(typeByPsiElement != null && context != null) {
      StatisticsManager.getInstance().incNameUseCount(typeByPsiElement, context, dcl.getName());
    }
  }

  public void registerTagWithId(String id, XmlTag tag) {
    myXmlId2TagMap.put(id,tag);
  }

  public XmlTag getTagById(String id) {
    return myXmlId2TagMap.get(id);
  }

  public void registerReference(PsiJavaReference ref, JavaResolveResult resolveResult) {
    PsiElement refElement = resolveResult.getElement();
    if (refElement != null && getFile().equals(refElement.getContainingFile())) {
      registerLocalRef(ref, refElement);
    }

    PsiElement resolveScope = resolveResult.getCurrentFileResolveScope();
    if (resolveScope instanceof PsiImportStatementBase) {
      registerImportStatement(ref, (PsiImportStatementBase)resolveScope);
    }
  }

  private void registerImportStatement (PsiReference ref, PsiImportStatementBase importStatement) {
    myImportStatements.put(ref, importStatement);
  }

  public boolean isRedundant(PsiImportStatementBase importStatement) {
    return !myImportStatements.containsValue(importStatement);
  }

  private void registerLocalRef(PsiReference ref, PsiElement refElement) {
    if (refElement instanceof PsiMethod && PsiTreeUtil.isAncestor(refElement, ref.getElement(), true)) return; // filter self-recursive calls
    if (refElement instanceof PsiClass && PsiTreeUtil.isAncestor(refElement, ref.getElement(), true)) return; // filter inner use of itself
    myLocalRefsMap.put(ref, refElement);
    if(refElement instanceof PsiNamedElement) {
      PsiNamedElement namedElement = (PsiNamedElement)refElement;
      if(!myUsedElements.contains(namedElement)) {
        myUsedElements.add(namedElement);
        addStatistics(namedElement);
      }
    }
  }

  public void removeInvalidRefs() {
    for(Iterator<PsiReference> iterator = myLocalRefsMap.keySet().iterator(); iterator.hasNext();){
      PsiReference ref = iterator.next();
      if (!ref.getElement().isValid()){
        PsiElement value = myLocalRefsMap.get(ref);
        iterator.remove();
        List<PsiReference> array = myLocalRefsMap.getKeysByValue(value);
        array.remove(ref);
      }
    }
    for (Iterator<PsiReference> iterator = myImportStatements.keySet().iterator(); iterator.hasNext();) {
      PsiReference ref = iterator.next();
      if (!ref.getElement().isValid()) iterator.remove();
    }
    for(Iterator<PsiNamedElement> iterator = myDclsUsedMap.keySet().iterator(); iterator.hasNext();) {
      PsiNamedElement element = iterator.next();
      
      if (!element.isValid()) iterator.remove();
    }
  }

  public boolean isReferenced(PsiElement element) {
    List<PsiReference> array = myLocalRefsMap.getKeysByValue(element);
    if(array != null && !isParameterUsedRecursively(element, array)) return true;

    Boolean usedStatus = myDclsUsedMap.get(element);
    return usedStatus == Boolean.TRUE;
  }

  private static boolean isParameterUsedRecursively(final PsiElement element, final List<PsiReference> array) {
    if (!(element instanceof PsiParameter)) return false;
    PsiParameter parameter = (PsiParameter)element;
    PsiElement scope = parameter.getDeclarationScope();
    if (!(scope instanceof PsiMethod)) return false;
    PsiMethod method = (PsiMethod)scope;
    int paramIndex = ArrayUtil.find(method.getParameterList().getParameters(), parameter);

    for (PsiReference reference : array) {
      if (!(reference instanceof PsiElement)) return false;
      PsiElement argument = (PsiElement)reference;

      PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)new PsiMatcherImpl(argument)
        .dot(PsiMatcherImpl.hasClass(PsiReferenceExpression.class))
        .parent(PsiMatcherImpl.hasClass(PsiExpressionList.class))
        .parent(PsiMatcherImpl.hasClass(PsiMethodCallExpression.class))
        .getElement();
      if (methodCallExpression == null) return false;
      PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      if (methodExpression == null || method != methodExpression.resolve()) return false;
      PsiExpressionList argumentList = methodCallExpression.getArgumentList();
      if (argumentList == null) return false;
      PsiExpression[] arguments = argumentList.getExpressions();
      int argumentIndex = ArrayUtil.find(arguments, argument);
      if (paramIndex != argumentIndex) return false;
    }

    return true;
  }

  public int getReadRefCount(PsiElement element) {
    LOG.assertTrue(element instanceof PsiVariable);
    List<PsiReference> array = myLocalRefsMap.getKeysByValue(element);
    if (array == null) return 0;
    int count = 0;
    for (PsiReference ref : array) {
      PsiElement refElement = ref.getElement();
      if (!(refElement instanceof PsiExpression)) { // possible with uncomplete code
        count++;
        continue;
      }
      if (PsiUtil.isAccessedForReading((PsiExpression)refElement)) {
        if (refElement.getParent() instanceof PsiExpression &&
            refElement.getParent().getParent() instanceof PsiExpressionStatement &&
            PsiUtil.isAccessedForWriting((PsiExpression)refElement)) {
          continue; // "var++;"
        }
        count++;
      }
    }
    return count;
  }

  public int getWriteRefCount(PsiElement element) {
    LOG.assertTrue(element instanceof PsiVariable);
    List<PsiReference> array = myLocalRefsMap.getKeysByValue(element);
    if (array == null) return 0;
    int count = 0;
    for (PsiReference ref : array) {
      final PsiElement refElement = ref.getElement();
      if (!(refElement instanceof PsiExpression)) { // possible with uncomplete code
        count++;
        continue;
      }
      if (PsiUtil.isAccessedForWriting((PsiExpression)refElement)) {
        count++;
      }
    }
    return count;
  }

  public PsiFile getFile() {
    return myFile;
  }

  public PsiNamedElement[] getUnusedDcls() {
    List<PsiNamedElement> result = new LinkedList<PsiNamedElement>();
    Set<Map.Entry<PsiNamedElement, Boolean>> entries = myDclsUsedMap.entrySet();

    for (final Map.Entry<PsiNamedElement, Boolean> entry : entries) {
      if (entry.getValue() == Boolean.FALSE) result.add(entry.getKey());
    }

    return result.toArray(new PsiNamedElement[result.size()]);
  }
}
