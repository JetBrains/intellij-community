/*
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Jul 13, 2007
 * Time: 2:09:28 PM
 */
package com.intellij.psi.util;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;

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

    return getProximity(element1) - getProximity(element2);
  }
  // 'distance' from the myContext, like in the following (closest to farthest):
  // -in the same module: distance=0
  // -in the same project: distance=1
  // -in the other project source: distance=2
  // -in the library: distance=3
  // -in the jdk: distance=4
  // -otherwise, distance=5
  private int getProximity(final PsiElement element) {
    if (myContext == null) return -1;
    Module contextModule = ModuleUtil.findModuleForPsiElement(myContext);
    if (contextModule == null) return -1;
    if (!element.isPhysical()) return -1;
    Module elementModule = ModuleUtil.findModuleForPsiElement(element);
    if (contextModule == elementModule) return 0;
    if (elementModule != null) {
      return elementModule.getProject() == contextModule.getProject() ? 1 : 2;
    }
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
    VirtualFile virtualFile = PsiUtil.getVirtualFile(element);
    List<OrderEntry> orderEntries = virtualFile == null ? Collections.<OrderEntry>emptyList() : fileIndex.getOrderEntriesForFile(virtualFile);

    if (orderEntries.isEmpty()) return 5;
    OrderEntry orderEntry = orderEntries.get(0);
    return orderEntry instanceof JdkOrderEntry ? 4 : 3;
  }
}