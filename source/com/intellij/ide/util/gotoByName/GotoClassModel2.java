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

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.navigation.ChooseByNameRegistry;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;

public class GotoClassModel2 extends ContributorsBasedGotoByModel {
  public GotoClassModel2(Project project) {
    super(project, ChooseByNameRegistry.getInstance().getClassModelContributors());
  }

  public String getPromptText() {
    return "Enter class name:";
  }

  public String getCheckBoxName() {
    return "Include non-project classes";
  }

  public String getNotInMessage() {
    return "no matches found in project";
  }

  public String getNotFoundMessage() {
    return "no matches found";
  }

  public char getCheckBoxMnemonic() {
    // Some combination like Alt+N, Ant+O, etc are a dead sysmbols, therefore
    // we have to change mnemonics for Mac users.
    return SystemInfo.isMac?'I':'n';
  }

  public boolean loadInitialCheckBoxState() {
    PropertiesComponent propertiesComponent = PropertiesComponent.getInstance(myProject);
    if ("true".equals(propertiesComponent.getValue("GoToClass.toSaveIncludeLibraries"))){
      return "true".equals(propertiesComponent.getValue("GoToClass.includeLibraries"));
    }
    else{
      return false;
    }
  }

  public void saveInitialCheckBoxState(boolean state) {
    PropertiesComponent propertiesComponent = PropertiesComponent.getInstance(myProject);
    if ("true".equals(propertiesComponent.getValue("GoToClass.toSaveIncludeLibraries"))){
      propertiesComponent.setValue("GoToClass.includeLibraries", state ? "true" : "false");
    }
  }
}