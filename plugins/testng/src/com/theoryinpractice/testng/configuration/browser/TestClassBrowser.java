// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.theoryinpractice.testng.configuration.browser;

import com.intellij.execution.configuration.BrowseModuleValueActionListener;
import com.intellij.ide.util.ClassFilter;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ex.MessagesEx;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.ContainerUtil;
import com.theoryinpractice.testng.MessageInfoException;
import com.theoryinpractice.testng.configuration.TestNGConfiguration;
import com.theoryinpractice.testng.configuration.TestNGConfigurationEditor;
import com.theoryinpractice.testng.model.TestClassFilter;

/**
 * @author Hani Suleiman
 */
public class TestClassBrowser extends BrowseModuleValueActionListener
{
  protected TestNGConfigurationEditor editor;

  public TestClassBrowser(Project project, TestNGConfigurationEditor editor) {
    super(project);
    this.editor = editor;
  }

  @Override
  protected String showDialog() {
    ClassFilter.ClassFilterWithScope filter;
    try {
      filter = getFilter();
    }
    catch (MessageInfoException e) {
      MessagesEx.MessageInfo message = e.getMessageInfo();
      message.showNow();
      return null;
    }
    TreeClassChooser chooser = TreeClassChooserFactory.getInstance(getProject()).createWithInnerClassesScopeChooser("Choose Test Class", filter.getScope(), filter, null);
    init(chooser);
    chooser.showDialog();
    PsiClass psiclass = chooser.getSelected();
    if (psiclass == null) {
      return null;
    } else {
      onClassChoosen(psiclass);
      return psiclass.getQualifiedName();
    }
  }

  protected void onClassChoosen(PsiClass psiClass) {
  }

  protected PsiClass findClass(String className) {
    return editor.getModuleSelector().findClass(className);
  }

  public ClassFilter.ClassFilterWithScope getFilter() throws MessageInfoException {
    TestNGConfiguration config = new TestNGConfiguration(getProject());
    editor.applyEditorTo(config);
    GlobalSearchScope scope = getSearchScope(config.getModules());
    if (scope == null) {
      scope = GlobalSearchScope.allScope(getProject());
    }
    return new TestClassFilter(scope, getProject(), false);
  }

  protected GlobalSearchScope getSearchScope(Module[] modules) {
    if (modules == null || modules.length == 0) return null;
    GlobalSearchScope[] scopes =
      ContainerUtil.map2Array(modules, GlobalSearchScope.class, module -> GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module));
    return GlobalSearchScope.union(scopes);
  }

  private void init(TreeClassChooser chooser) {
    String s = getText();
    PsiClass psiclass = findClass(s);
    if (psiclass == null)
      return;
    PsiDirectory psidirectory = psiclass.getContainingFile().getContainingDirectory();
    if (psidirectory != null)
      chooser.selectDirectory(psidirectory);
    chooser.select(psiclass);
  }
}
