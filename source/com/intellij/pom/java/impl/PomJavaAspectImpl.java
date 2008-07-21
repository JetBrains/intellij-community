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
package com.intellij.pom.java.impl;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.PomManager;
import com.intellij.pom.PomModel;
import com.intellij.pom.PomModelAspect;
import com.intellij.pom.event.PomModelEvent;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.pom.java.PomJavaAspect;
import com.intellij.pom.java.events.JavaTreeChanged;
import com.intellij.pom.java.events.PomJavaAspectChangeSet;
import com.intellij.pom.tree.TreeAspect;
import com.intellij.pom.tree.events.TreeChangeEvent;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

public class PomJavaAspectImpl extends PomJavaAspect implements ProjectComponent {
  private final Project myProject;
  private final PsiManager myPsiManager;

  public PomJavaAspectImpl(Project project, PsiManager psiManager, TreeAspect treeAspect) {
    myProject = project;
    myPsiManager = psiManager;
    PomManager.getModel(project).registerAspect(PomJavaAspect.class, this, Collections.singleton((PomModelAspect)treeAspect));
  }

  public LanguageLevel getLanguageLevel() {
    return LanguageLevelProjectExtension.getInstance(myPsiManager.getProject()).getLanguageLevel();
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  @NotNull
  public String getComponentName() {
    return "PomJavaModel";
  }

  public void initComponent() { }

  public void disposeComponent() {
  }

  public void update(PomModelEvent event) {
    final PomModel model = PomManager.getModel(myProject);
    final TreeChangeEvent changeSet = (TreeChangeEvent)event.getChangeSet(model.getModelAspect(TreeAspect.class));
    if(changeSet == null) return;
    final PsiFile containingFile = changeSet.getRootElement().getPsi().getContainingFile();
    if(!(containingFile instanceof PsiJavaFile)) return;
    final PomJavaAspectChangeSet set = new PomJavaAspectChangeSet(model, (PsiJavaFile) containingFile);
    set.addChange(new JavaTreeChanged(containingFile));
    event.registerChangeSet(PomJavaAspectImpl.this, set);
  }
}