/*
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Jul 13, 2007
 * Time: 2:09:28 PM
 */
package com.intellij.psi.util;

import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class PsiProximityComparator implements Comparator<Object> {
  private final PsiElement myContext;
  private final Project myProject;

  public PsiProximityComparator(PsiElement context, Project project) {
    myContext = context;
    myProject = project;
  }

  public int compare(final Object o1, final Object o2) {
    PsiElement element1 = o1 instanceof PsiElement ? (PsiElement)o1 : null;
    PsiElement element2 = o2 instanceof PsiElement ? (PsiElement)o2 : null;
    if (element1 == null) return element2 == null ? 0 : 1;
    if (element2 == null) return -1;

    final PsiProximity proximity1 = getProximity(element1);
    final PsiProximity proximity2 = getProximity(element2);
    return proximity1 != null && proximity2 != null ? proximity1.compareTo(proximity2) : 0;
  }

  @Nullable
  public final PsiProximity getProximity(final PsiElement element) {
    if (myContext == null) return null;
    Module contextModule = ModuleUtil.findModuleForPsiElement(myContext);
    if (contextModule == null) return null;

    final PsiElement commonContext = PsiTreeUtil.findCommonContext(myContext, element);
    if (PsiTreeUtil.getContextOfType(commonContext, PsiMethod.class, false) != null) return PsiProximity.SAME_METHOD;
    if (PsiTreeUtil.getContextOfType(commonContext, PsiClass.class, false) != null) return PsiProximity.SAME_CLASS;

    VirtualFile virtualFile = PsiUtil.getVirtualFile(element);
    if (isOpenedInEditor(virtualFile)) return PsiProximity.OPENED_IN_EDITOR;

    if (element instanceof PsiClass) {
      final String qname = ((PsiClass) element).getQualifiedName();
      if (qname != null) {
        final PsiJavaFile psiJavaFile = PsiTreeUtil.getContextOfType(myContext, PsiJavaFile.class, false);
        if (psiJavaFile != null) {
          final PsiImportList importList = psiJavaFile.getImportList();
          if (importList != null) {
            for (final PsiImportStatement importStatement : importList.getImportStatements()) {
              if (!importStatement.isOnDemand() && qname.equals(importStatement.getQualifiedName())) {
                return PsiProximity.EXPLICITLY_IMPORTED;
              }
            }
          }
        }
      }
    }

    PsiClass placeClass = PsiTreeUtil.getContextOfType(element, PsiClass.class, false);
    if (myContext.getParent() instanceof PsiReferenceExpression) {
      final PsiExpression qualifierExpression = ((PsiReferenceExpression)myContext.getParent()).getQualifierExpression();
      if (qualifierExpression != null) {
        final PsiType type = qualifierExpression.getType();
        if (type instanceof PsiClassType) {
          final PsiClass psiClass = ((PsiClassType)type).resolve();
          if (psiClass != null) {
            placeClass = psiClass;
          }
        }
      }
    }

    PsiClass contextClass = PsiTreeUtil.getContextOfType(myContext, PsiClass.class, false);
    while (contextClass != null) {
      PsiClass elementClass = placeClass;
      while (elementClass != null) {
        if (contextClass.isInheritor(elementClass, true)) return PsiProximity.SUPERCLASS;
        elementClass = elementClass.getContainingClass();
      }
      contextClass = contextClass.getContainingClass();
    }

    Module elementModule = ModuleUtil.findModuleForPsiElement(element);
    if (contextModule == elementModule) {
      final PsiPackage psiPackage = PsiTreeUtil.getContextOfType(myContext, PsiPackage.class, false);
      if (psiPackage != null && psiPackage.equals(PsiTreeUtil.getContextOfType(element, PsiPackage.class, false))) return PsiProximity.SAME_MODULE_AND_PACKAGE;
      return PsiProximity.SAME_MODULE;
    }
    if (elementModule != null) {
      return elementModule.getProject() == contextModule.getProject() ? PsiProximity.SAME_PROJECT : PsiProximity.OTHER_PROJECT;
    }
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
    List<OrderEntry> orderEntries = virtualFile == null ? Collections.<OrderEntry>emptyList() : fileIndex.getOrderEntriesForFile(virtualFile);

    if (orderEntries.isEmpty()) return null;
    OrderEntry orderEntry = orderEntries.get(0);
    return orderEntry instanceof JdkOrderEntry ? PsiProximity.JDK : PsiProximity.LIBRARY;
  }

  private boolean isOpenedInEditor(VirtualFile element) {
    return element != null && ArrayUtil.find(FileEditorManager.getInstance(myProject).getOpenFiles(), element) != -1;
  }
}