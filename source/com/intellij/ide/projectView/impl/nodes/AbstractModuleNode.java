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
package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.IconUtilEx;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractModuleNode extends ProjectViewNode<Module> {
  protected AbstractModuleNode(Project project, Module module, ViewSettings viewSettings) {
    super(project, module, viewSettings);
  }

  public void update(PresentationData presentation) {
    if (getValue().isDisposed()) {
      setValue(null);
      return;
    }
    presentation.setPresentableText(getValue().getName());
    presentation.setOpenIcon(IconUtilEx.getIcon(getValue(), Iconable.ICON_FLAG_OPEN));
    presentation.setClosedIcon(IconUtilEx.getIcon(getValue(), Iconable.ICON_FLAG_CLOSED));
  }


  public String getTestPresentation() {
    return "Module";
  }

  public boolean contains(@NotNull VirtualFile file) {
    Module module = getValue();
    return module != null && (PackageUtil.moduleContainsFile(module, file, false) ||
           PackageUtil.moduleContainsFile(module, file, true));
  }

  public String getToolTip() {
    final Module module = getValue();
    return module.getModuleType().getName();
  }

  public void navigate(final boolean requestFocus) {
    ModulesConfigurator.showDialog(getProject(), getValue().getName(), null, false);
  }

  public boolean canNavigate() {
    return true;
  }

  public boolean canNavigateToSource() {
    return false;
  }
}
