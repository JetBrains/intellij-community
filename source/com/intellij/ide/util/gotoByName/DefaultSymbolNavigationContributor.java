/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.ide.util.gotoByName;

import com.intellij.navigation.ChooseByNameContributor;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.HashSet;

import java.util.*;

public class DefaultSymbolNavigationContributor implements ChooseByNameContributor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.util.gotoByName.DefaultSymbolNavigationContributor");
  public DefaultSymbolNavigationContributor() {
  }

  public String[] getNames(Project project, boolean includeNonProjectItems) {
    PsiShortNamesCache cache = PsiManager.getInstance(project).getShortNamesCache();
    HashSet<String> set = new HashSet<String>();
    cache.getAllMethodNames(includeNonProjectItems, set);
    cache.getAllFieldNames(includeNonProjectItems, set);
    cache.getAllClassNames(includeNonProjectItems, set);
    return set.toArray(new String[set.size()]);
  }

  public NavigationItem[] getItemsByName(String name, Project project, boolean includeNonProjectItems) {
    GlobalSearchScope scope = includeNonProjectItems ? GlobalSearchScope.allScope(project) : GlobalSearchScope.projectScope(project);
    PsiShortNamesCache cache = PsiManager.getInstance(project).getShortNamesCache();

    PsiMethod[] methods = cache.getMethodsByName(name, scope);
    methods = filterInheritedMethods(methods);
    PsiField[] fields = cache.getFieldsByName(name, scope);
    PsiClass[] classes = cache.getClassesByName(name, scope);

    List<PsiMember> result = new ArrayList<PsiMember>();
    result.addAll(Arrays.asList(methods));
    result.addAll(Arrays.asList(fields));
    result.addAll(Arrays.asList(classes));
    filterOutNonOpenable(result);
    PsiMember[] array = result.toArray(new PsiMember[result.size()]);
    Arrays.sort(array, MyComparator.INSTANCE);
    return array;
  }

  private void filterOutNonOpenable(List<PsiMember> members) {
    ListIterator<PsiMember> it = members.listIterator();
    while (it.hasNext()) {
      PsiMember member = it.next();
      if (isNonOpenable(member)) {
        it.remove();
      }
    }
  }

  private boolean isNonOpenable(PsiMember member) {
    return member.getContainingFile().getVirtualFile() == null;
  }

  private PsiMethod[] filterInheritedMethods(PsiMethod[] methods) {
    ArrayList<PsiMethod> list = new ArrayList<PsiMethod>();
    for (PsiMethod method : methods) {
      if (method.isConstructor()) continue;
      PsiMethod[] supers = method.findSuperMethods();
      if (supers.length > 0) continue;
      list.add(method);
    }
    return list.toArray(new PsiMethod[list.size()]);
  }

  private static class MyComparator implements Comparator<PsiModifierListOwner>{
    public static final MyComparator INSTANCE = new MyComparator();

    private final GotoSymbolCellRenderer myRenderer = new GotoSymbolCellRenderer();

    public int compare(PsiModifierListOwner element1, PsiModifierListOwner element2) {
      if (element1 == element2) return 0;

      PsiModifierList modifierList1 = element1.getModifierList();
      LOG.assertTrue(modifierList1 != null, element1.getText());
      PsiModifierList modifierList2 = element2.getModifierList();
      LOG.assertTrue(modifierList2 != null, element2.getText());
      int level1 = PsiUtil.getAccessLevel(modifierList1);
      int level2 = PsiUtil.getAccessLevel(modifierList2);
      if (level1 != level2) return level2 - level1;

      int kind1 = getElementTypeLevel(element1);
      int kind2 = getElementTypeLevel(element2);
      if (kind1 != kind2) return kind1 - kind2;

      if (element1 instanceof PsiMethod){
        LOG.assertTrue(element2 instanceof PsiMethod);
        PsiParameter[] parms1 = ((PsiMethod)element1).getParameterList().getParameters();
        PsiParameter[] parms2 = ((PsiMethod)element2).getParameterList().getParameters();

        if (parms1.length != parms2.length) return parms1.length - parms2.length;
      }

      String text1 = myRenderer.getElementText(element1);
      String text2 = myRenderer.getElementText(element2);
      if (!text1.equals(text2)) return text1.compareTo(text2);

      String containerText1 = myRenderer.getContainerText(element1, text1);
      String containerText2 = myRenderer.getContainerText(element2, text2);
      if (containerText1 == null) containerText1 = "";
      if (containerText2 == null) containerText2 = "";
      return containerText1.compareTo(containerText2);
    }

    private int getElementTypeLevel(PsiElement element){
      if (element instanceof PsiMethod){
        return 1;
      }
      else if (element instanceof PsiField){
        return 2;
      }
      else if (element instanceof PsiClass){
        return 3;
      }
      else{
        LOG.error(element.toString());
        return 0;
      }
    }
  }

}