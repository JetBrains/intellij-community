package com.intellij.structuralsearch.impl.matcher.iterators;

import com.intellij.psi.*;
import com.intellij.structuralsearch.impl.matcher.MatchUtils;

import java.util.ArrayList;

/**
 * Passes the hierarchy
 */
public class HierarchyNodeIterator extends NodeIterator {
  private int index;
  private ArrayList<PsiElement> remaining;
  private boolean objectTaken;
  private boolean acceptClasses;
  private boolean acceptInterfaces;

  private void build(PsiElement current) {

    if (current!=null) {
      if (MatchUtils.compareWithNoDifferenceToPackage(current.getText(),"Object")) {
        if(objectTaken) return;
        objectTaken = true;
      }

      PsiElement element = MatchUtils.getReferencedElement(current);
      if (!(element instanceof PsiClass)) {
        remaining.add(element);
        return;
      }
      PsiClass clazz = (PsiClass) element;
      if (clazz!=null) {
        remaining.add(clazz);
        if (clazz instanceof PsiAnonymousClass) {
          build(((PsiAnonymousClass)clazz).getBaseClassReference());
          return;
        }

        if (acceptClasses) {
          final PsiReferenceList clazzExtendsList = clazz.getExtendsList();
          final PsiElement[] extendsList = (clazzExtendsList != null)?clazzExtendsList.getReferenceElements():null;

          if (extendsList!=null) {
            for(int i=0;i<extendsList.length;++i) {
              build(extendsList[i]);
            }
          } else {
            /*
            if (!objectTaken) {
              remaining.add("Object");
              objectTaken = true;
            }*/
          }
        }

        if (acceptInterfaces) {
          final PsiElement[] implementsList = clazz.getImplementsList().getReferenceElements();
          if (implementsList!=null) {
            for(int i=0;i<implementsList.length;++i) {
              build(implementsList[i]);
            }
          }
        }
      } else {
        remaining.add(current);
      }
    }
  }

  public HierarchyNodeIterator(PsiElement reference, boolean acceptClasses, boolean acceptInterfaces) {
    remaining = new ArrayList<PsiElement>();
    this.acceptClasses = acceptClasses;
    this.acceptInterfaces = acceptInterfaces;

    if (reference instanceof PsiIdentifier) {
      reference = reference.getParent();
    }

    build(reference);
  }

  public boolean hasNext() {
    return index < remaining.size();
  }

  public PsiElement current() {
    return remaining.get(index);
  }

  public void advance() {
    if (index!=remaining.size()) {
      ++index;
    }
  }

  public void rewind() {
    if (index > 0) {
      --index;
    }
  }

  public void reset() {
    index = 0;
  }
}
