package com.intellij.structuralsearch.impl.matcher.iterators;

import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.structuralsearch.impl.matcher.MatchUtils;
import com.intellij.openapi.project.Project;

import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;

/**
 * Passes the hierarchy
 */
public class HierarchyNodeIterator extends NodeIterator {
  private int index;
  private ArrayList<PsiElement> remaining;
  private boolean objectTaken;
  private boolean firstElementTaken;
  private final boolean acceptClasses;
  private final boolean acceptInterfaces;
  private final boolean acceptFirstElement;

  private void build(PsiElement current, Set<PsiElement> visited) {

    if (current!=null) {
      final String str = current instanceof PsiClass ? ((PsiClass)current).getName():current.getText();

      if (MatchUtils.compareWithNoDifferenceToPackage(str,"Object")) {
        if(objectTaken) return;
        objectTaken = true;
      }

      PsiElement element = MatchUtils.getReferencedElement(current);

      if (element instanceof PsiClass) {
        if (visited.contains(element)) return;
        final PsiClass clazz = (PsiClass)element;

        if (acceptInterfaces || !clazz.isInterface() ) visited.add(element);

        if (!firstElementTaken && acceptFirstElement || firstElementTaken) remaining.add(clazz);
        firstElementTaken = true;

        if (clazz instanceof PsiAnonymousClass) {
          build(((PsiAnonymousClass)clazz).getBaseClassReference(),visited);
          return;
        }

        if (acceptClasses) {
          processClasses(clazz, visited);

          if (!objectTaken) {
            final Project project = clazz.getProject();
            final PsiClassType javaLangObject = PsiType.getJavaLangObject(PsiManager.getInstance(project), GlobalSearchScope.allScope(project));
            build( javaLangObject.resolve(), visited);
          }
        }

        if (acceptInterfaces) {
          final PsiReferenceList implementsList = clazz.getImplementsList();

          if (implementsList != null) {
            final PsiElement[] implementsListElements = implementsList.getReferenceElements();

            for (PsiElement anImplementsList : implementsListElements) {
              build(anImplementsList,visited);
            }
          }

          if (!acceptClasses) processClasses(clazz, visited);
        }
      } else {
        remaining.add(current);
      }
    }
  }

  private void processClasses(final PsiClass clazz, final Set<PsiElement> visited) {
    final PsiReferenceList clazzExtendsList = clazz.getExtendsList();
    final PsiElement[] extendsList = (clazzExtendsList != null)?clazzExtendsList.getReferenceElements():null;

    if (extendsList != null) {
      for (PsiElement anExtendsList : extendsList) {
        build(anExtendsList,visited);
      }
    }
  }

  public HierarchyNodeIterator(PsiElement reference, boolean acceptClasses, boolean acceptInterfaces) {
    this(reference, acceptClasses, acceptInterfaces, true);
  }

  public HierarchyNodeIterator(PsiElement reference, boolean acceptClasses, boolean acceptInterfaces, boolean acceptFirstElement) {
    remaining = new ArrayList<PsiElement>();
    this.acceptClasses = acceptClasses;
    this.acceptInterfaces = acceptInterfaces;
    this.acceptFirstElement = acceptFirstElement;

    if (reference instanceof PsiIdentifier) {
      reference = reference.getParent();
    }

    build(reference,new HashSet<PsiElement>());
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
