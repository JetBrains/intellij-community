package com.intellij.psi.impl.search;

import com.intellij.find.findUsages.FindUsagesOptions;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.Processor;

import java.util.HashSet;

/**
 * Author: msk
 */
public class ThrowSearchUtil {

  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.search.ThrowSearchUtil");

  private final PsiManager      myManager;
  private final PsiSearchHelper myHelper;
  private final Processor<UsageInfo> myResults;
  private final FindUsagesOptions myOptions;
  private final Root myRoot;

  private final HashSet<PsiMethod> myReadySet;

  public static class Root {
    final PsiElement myElement;
    final PsiType    myType;
    final boolean    isExact;

    public Root(final PsiElement root, final PsiType type, final boolean exact) {
      myElement = root;
      myType = type;
      isExact = exact;
    }

    public String toString() {
      return PsiFormatUtil.formatType (myType, PsiFormatUtil.SHOW_FQ_CLASS_NAMES, PsiSubstitutor.EMPTY);
    }
  }

  public ThrowSearchUtil (final Processor<UsageInfo> processor, final Root root, final FindUsagesOptions options) {
    myRoot = root;
    myResults = processor;
    myOptions = options;
    myManager = myRoot.myElement.getManager ();
    myHelper = myManager.getSearchHelper();
    myReadySet = new HashSet<PsiMethod>();
  }

  /**
   * @param aCatch
   * @return true, if we should continue processing
   */

  private boolean processExn (final PsiParameter aCatch) {
    final PsiType type = aCatch.getType();
    if (type.isAssignableFrom(myRoot.myType)) {
      myResults.process (new UsageInfo (aCatch));
      return false;
    }
    else if (myOptions.isStrictThrowUsages && !myRoot.isExact && myRoot.myType.isAssignableFrom (type)) {
        myResults.process (new UsageInfo (aCatch));
        return true;
    }
    else {
      return true;
    }
  }

  private void scanCatches (PsiElement elem)
  {
    while (elem != null) {
      final PsiElement parent = elem.getParent();
      if (elem instanceof PsiMethod) {
        final PsiMethod method = (PsiMethod) elem;
        if (!myReadySet.contains(method)) {
          myReadySet.add(method);
          final PsiReference[] refs = myHelper.findReferencesIncludingOverriding(method, myOptions.searchScope, true);
          for (int i = 0; i != refs.length; ++ i)
            scanCatches(refs [i].getElement ());
        }
        return;
      }
      else if (elem instanceof PsiTryStatement) {
        final PsiTryStatement aTry = (PsiTryStatement) elem;
        final PsiParameter[] catches = aTry.getCatchBlockParameters();
        for (int i = 0; i != catches.length; ++ i) {
          if (!processExn(catches[i])) {
            return;
          }
        }
      }
      else if (parent instanceof PsiTryStatement) {
        final PsiTryStatement tryStmt = (PsiTryStatement) parent;
        if (elem != tryStmt.getTryBlock()) {
          elem = parent.getParent();
          continue;
        }
      }
      elem = parent;
    }
  }

  public void run() {
    scanCatches (myRoot.myElement);
  }

  /**
   *
   * @param exn
   * @return is type of exn exactly known
   */

  public static boolean isExactExnType(final PsiExpression exn) {
    if (exn instanceof PsiNewExpression)
      return true;
    else
      return false;
  }

  public static Root [] getSearchRoots (final PsiElement element) {
    if (element instanceof PsiThrowStatement) {
      final PsiThrowStatement aThrow = (PsiThrowStatement) element;
      final PsiExpression exn = aThrow.getException();
      return new Root[]{new Root (aThrow.getParent(), exn.getType(), isExactExnType(exn))};
    }
    if (element instanceof PsiKeyword) {
      final PsiKeyword kwd = (PsiKeyword) element;
      if (PsiKeyword.THROWS.equals (kwd.getText())) {
        final PsiElement parent = kwd.getParent();
        if (parent != null && parent.getParent() instanceof PsiMethod) {
          final PsiMethod method = (PsiMethod) parent.getParent();
          final PsiReferenceList throwsList = method.getThrowsList();
          final PsiClassType[] exns = throwsList.getReferencedTypes();
          final Root [] roots = new Root [exns.length];
          for (int i = 0; i != roots.length; ++ i) {
            final PsiClassType exn = exns [i];
            roots [i] = new Root (method, exn, false); // TODO: test for final
          }
          return roots;
        }
      }
    }
    return null;
  }

  public static boolean isSearchable(final PsiElement element) {
    return getSearchRoots (element) != null;
  }

  public static String getSearchableTypeName(final PsiElement e) {
    if (e instanceof PsiThrowStatement) {
      final PsiThrowStatement aThrow = (PsiThrowStatement) e;
      final PsiType type = aThrow.getException ().getType ();
      return PsiFormatUtil.formatType (type, PsiFormatUtil.SHOW_FQ_CLASS_NAMES, PsiSubstitutor.EMPTY);
    }
    if (e instanceof PsiKeyword && PsiKeyword.THROWS.equals (e.getText())) {
      return e.getParent().getText ();
    }
    LOG.error ("invalid searchable element");
    return e.getText();
  }

}
