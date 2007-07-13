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

import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.navigation.ChooseByNameRegistry;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.psi.PsiClass;
import com.intellij.psi.presentation.java.SymbolPresentationUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GotoClassModel2 extends ContributorsBasedGotoByModel {
  public GotoClassModel2(Project project) {
    super(project, ChooseByNameRegistry.getInstance().getClassModelContributors());
  }

  @Nullable
  public String getPromptText() {
    return IdeBundle.message("prompt.gotoclass.enter.class.name");
  }

  public String getCheckBoxName() {
    return IdeBundle.message("checkbox.include.non.project.classes");
  }

  public String getNotInMessage() {
    return IdeBundle.message("label.no.matches.found.in.project");
  }

  public String getNotFoundMessage() {
    return IdeBundle.message("label.no.matches.found");
  }

  public char getCheckBoxMnemonic() {
    // Some combination like Alt+N, Ant+O, etc are a dead symbols, therefore
    // we have to change mnemonics for Mac users.
    return SystemInfo.isMac?'P':'n';
  }

  public boolean loadInitialCheckBoxState() {
    PropertiesComponent propertiesComponent = PropertiesComponent.getInstance(myProject);
    return Boolean.TRUE.toString().equals(propertiesComponent.getValue("GoToClass.toSaveIncludeLibraries")) &&
           Boolean.TRUE.toString().equals(propertiesComponent.getValue("GoToClass.includeLibraries"));
  }

  public void saveInitialCheckBoxState(boolean state) {
    PropertiesComponent propertiesComponent = PropertiesComponent.getInstance(myProject);
    if (Boolean.TRUE.toString().equals(propertiesComponent.getValue("GoToClass.toSaveIncludeLibraries"))){
      propertiesComponent.setValue("GoToClass.includeLibraries", Boolean.toString(state));
    }
  }

  public String getFullName(final Object element) {
    if (element instanceof PsiClass) {
      final PsiClass psiClass = (PsiClass)element;
      final String qName = psiClass.getQualifiedName();
      if (qName != null) return qName;

      final String containerText = SymbolPresentationUtil.getSymbolContainerText(psiClass);
      return containerText + "." + psiClass.getName();
    }

    return getElementName(element);
  }

  @NotNull
  public String[] getSeparators() {
    return new String[] {"."};
  }
}