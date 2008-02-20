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

import com.intellij.navigation.GotoClassContributor;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.presentation.java.SymbolPresentationUtil;
import com.intellij.psi.search.GlobalSearchScope;

import java.util.ArrayList;

public class DefaultClassNavigationContributor implements GotoClassContributor {
  public DefaultClassNavigationContributor() {
  }

  public String[] getNames(Project project, boolean includeNonProjectItems) {
    return JavaPsiFacade.getInstance(project).getShortNamesCache().getAllClassNames(includeNonProjectItems);
  }

  public NavigationItem[] getItemsByName(String name, final String pattern, Project project, boolean includeNonProjectItems) {
    final GlobalSearchScope scope = includeNonProjectItems ? GlobalSearchScope.allScope(project) : GlobalSearchScope.projectScope(project);
    return filterUnshowable(JavaPsiFacade.getInstance(project).getShortNamesCache().getClassesByName(name, scope), pattern);
  }

  private static NavigationItem[] filterUnshowable(PsiClass[] items, final String pattern) {
    boolean isAnnotation = pattern.startsWith("@");
    ArrayList<NavigationItem> list = new ArrayList<NavigationItem>(items.length);
    for (PsiClass item : items) {
      if (item.getContainingFile().getVirtualFile() == null) continue;
      if (isAnnotation && !item.isAnnotationType()) continue;
      list.add(item);
    }
    return list.toArray(new NavigationItem[list.size()]);
  }

  public String getQualifiedName(final NavigationItem item) {
    if (item instanceof PsiClass) {
      final PsiClass psiClass = (PsiClass)item;
      final String qName = psiClass.getQualifiedName();
      if (qName != null) return qName;

      final String containerText = SymbolPresentationUtil.getSymbolContainerText(psiClass);
      return containerText + "." + psiClass.getName();
    }
    return null;
  }
}