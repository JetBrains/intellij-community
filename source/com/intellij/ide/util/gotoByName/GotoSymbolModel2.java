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
import com.intellij.navigation.ChooseByNameRegistry;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.psi.PsiElement;
import com.intellij.psi.presentation.java.SymbolPresentationUtil;
import org.jetbrains.annotations.NotNull;

public class GotoSymbolModel2 extends ContributorsBasedGotoByModel {
  public GotoSymbolModel2(Project project) {
    super(project, ChooseByNameRegistry.getInstance().getSymbolModelContributors());
  }

  public String getPromptText() {
    return IdeBundle.message("prompt.gotosymbol.enter.symbol.name");
  }

  public String getCheckBoxName() {
    return IdeBundle.message("checkbox.include.non.project.symbols");
  }

  public String getNotInMessage() {
    return IdeBundle.message("label.no.matches.found.in.project");
  }

  public String getNotFoundMessage() {
    return IdeBundle.message("label.no.matches.found");
  }

  public char getCheckBoxMnemonic() {
    // Some combination like Alt+N, Ant+O, etc are a dead sysmbols, therefore
    // we have to change mnemonics for Mac users.
    return SystemInfo.isMac?'P':'n';
  }

  public boolean loadInitialCheckBoxState() {
    return false;
  }

  public void saveInitialCheckBoxState(boolean state) {
  }

  public String getFullName(final Object element) {
    if (element instanceof PsiElement) {
      final PsiElement psiElement = (PsiElement)element;

      final String containerText = SymbolPresentationUtil.getSymbolContainerText(psiElement);
      return containerText + "." + getElementName(element);
    }

    return getElementName(element);
  }

  @NotNull
  public String[] getSeparators() {
    return new String[] {".", ":"};
  }
}