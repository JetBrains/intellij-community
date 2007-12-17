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

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.pom.*;
import com.intellij.pom.event.PomModelEvent;
import com.intellij.pom.impl.PomTransactionBase;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.pom.java.PomJavaAspect;
import com.intellij.pom.java.PomPackage;
import com.intellij.pom.java.events.JavaTreeChanged;
import com.intellij.pom.java.events.PomJavaAspectChangeSet;
import com.intellij.pom.tree.TreeAspect;
import com.intellij.pom.tree.events.TreeChangeEvent;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;

import java.util.*;

public class PomJavaAspectImpl extends PomJavaAspect implements ProjectComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.pom.java.impl.PomJavaAspectImpl");
  private final Project myProject;
  private final PsiManager myPsiManager;
  private PomPackage myRootPackage;
  private Map<String, PomPackageImpl> myPackageMap = new HashMap<String, PomPackageImpl>();

  public PomJavaAspectImpl(Project project, PsiManager psiManager, TreeAspect treeAspect) {
    myProject = project;
    myPsiManager = psiManager;
    myRootPackage = new PomPackageImpl("", null, this);
    PomManager.getModel(project).registerAspect(PomJavaAspect.class, this, Collections.singleton((PomModelAspect)treeAspect));
  }

  public PomPackage getRootPackage() {
    return myRootPackage;
  }

  public PsiManager getPsiManager() {
    return myPsiManager;
  }

  public PomPackage findPackage(String fqn) {
    if (fqn == null || fqn.length() == 0) return myRootPackage;
    PomPackageImpl pomPackage = myPackageMap.get(fqn);
    if (pomPackage == null) {
      final String name;
      final String prefix;
      int idx = fqn.lastIndexOf('.');
      if (idx < 0) {
        name = fqn;
        prefix = null;
      }
      else {
        prefix = fqn.substring(0, idx);
        name = fqn.substring(idx + 1);
      }

      pomPackage = new PomPackageImpl(name, (PomPackageImpl)findPackage(prefix), this);
      myPackageMap.put(fqn, pomPackage);
    }
    return pomPackage;
  }

  public LanguageLevel getLanguageLevel() {
    return JavaPsiFacade.getInstance(myPsiManager.getProject()).getEffectiveLanguageLevel();
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  public String getComponentName() {
    return "PomJavaModel";
  }

  public void initComponent() { }

  public void disposeComponent() {
  }

  public PomPackage[] getSubPackages(PomPackageImpl pomPackage) {
    List<PomPackage> subs = new ArrayList<PomPackage>();
    for (Iterator<PomPackageImpl> iterator = myPackageMap.values().iterator(); iterator.hasNext();) {
      PomPackageImpl aPackage = iterator.next();
      if (aPackage.getParentPackage() == pomPackage) subs.add(aPackage);
    }
    return subs.toArray(new PomPackage[subs.size()]);
  }

  public void update(PomModelEvent event) {
    final PomModel model = PomManager.getModel(myProject);
    final TreeChangeEvent changeSet = (TreeChangeEvent)event.getChangeSet(model.getModelAspect(TreeAspect.class));
    if(changeSet == null) return;
    final PsiFile containingFile = changeSet.getRootElement().getPsi().getContainingFile();
    if(!(containingFile.getLanguage() instanceof JavaLanguage)) return;
    final PomJavaAspectChangeSet set = new PomJavaAspectChangeSet(model, containingFile);
    set.addChange(new JavaTreeChanged(containingFile));
    event.registerChangeSet(PomJavaAspectImpl.this, set);
  }

  public PomModelEvent getEvent() {
    return null;
  }

  public PomElement getMorph(PomElement element) {
    //TODO
    return null;
  }

  private void firePomEvent(final PsiFile file) {
    if (isJavaFile(file)) {
      final PomModel model = PomManager.getModel(myProject);
      try {
        PomManager.getModel(myProject).runTransaction(new PomTransactionBase(file, this) {
          public PomModelEvent runInner() {
            final PomModelEvent event = new PomModelEvent(model);
            final PomJavaAspectChangeSet set = new PomJavaAspectChangeSet(model, file);
            set.addChange(new JavaTreeChanged(file));
            event.registerChangeSet(PomJavaAspectImpl.this, set);
            return event;
          }
        });
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
  }

  private boolean isJavaFile(final PsiFile file) {
    return file instanceof PsiJavaFile || file instanceof PsiCodeFragment;
  }
}