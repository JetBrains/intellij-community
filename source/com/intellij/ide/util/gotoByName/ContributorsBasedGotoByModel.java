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

import com.intellij.ide.util.NavigationItemListCellRenderer;
import com.intellij.navigation.ChooseByNameContributor;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.project.Project;

import javax.swing.*;
import java.util.*;

public abstract class ContributorsBasedGotoByModel implements ChooseByNameModel {
  protected final Project myProject;
  private final ChooseByNameContributor[] myContributors;

  protected ContributorsBasedGotoByModel(Project project, ChooseByNameContributor[] contributors) {
    myProject = project;
    myContributors = contributors;
  }

  public ListCellRenderer getListCellRenderer() {
    return new NavigationItemListCellRenderer();
  }

  public String[] getNames(boolean checkBoxState) {
    Set<String> names = new HashSet<String>();
    for (int i = 0; i < myContributors.length; i++) {
      ChooseByNameContributor contributor = myContributors[i];
      names.addAll(Arrays.asList(contributor.getNames(myProject, checkBoxState)));
    }

    return names.toArray(new String[names.size()]);
  }

  public Object[] getElementsByName(String name, boolean checkBoxState) {
    List<NavigationItem> items = new ArrayList<NavigationItem>();
    for (int i = 0; i < myContributors.length; i++) {
      ChooseByNameContributor contributor = myContributors[i];
      items.addAll(Arrays.asList(contributor.getItemsByName(name, myProject, checkBoxState)));
    }
    return items.toArray();
  }

  public String getElementName(Object element) {
    return ((NavigationItem)element).getName();
  }

}