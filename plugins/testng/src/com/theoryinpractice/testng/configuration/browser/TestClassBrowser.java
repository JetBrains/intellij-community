/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.theoryinpractice.testng.configuration.browser;

import com.intellij.execution.configuration.BrowseModuleValueActionListener;
import com.intellij.ide.util.ClassFilter;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ex.MessagesEx;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.theoryinpractice.testng.MessageInfoException;
import com.theoryinpractice.testng.configuration.TestNGConfiguration;
import com.theoryinpractice.testng.configuration.TestNGConfigurationEditor;
import com.theoryinpractice.testng.configuration.TestNGConfigurationType;
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
    TestNGConfiguration config = new TestNGConfiguration("<no-name>", getProject(), TestNGConfigurationType.getInstance().getConfigurationFactories()[0]);
    editor.applyEditorTo(config);
    GlobalSearchScope scope = getSearchScope(config.getModules());
    if (scope == null) {
      scope = GlobalSearchScope.allScope(getProject());
    }
    return new TestClassFilter(scope, getProject(), false);
  }

  protected GlobalSearchScope getSearchScope(Module[] modules) {
    if (modules == null || modules.length == 0) return null;
    GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesScope(modules[0]);
    for (int i = 1; i < modules.length; i++) {
      scope.uniteWith(GlobalSearchScope.moduleWithDependenciesScope(modules[i]));
    }
    return scope;
  }

  private void init(TreeClassChooser chooser) {
    String s = getText();
    PsiClass psiclass = findClass(s);
    if (psiclass == null)
      return;
    com.intellij.psi.PsiDirectory psidirectory = psiclass.getContainingFile().getContainingDirectory();
    if (psidirectory != null)
      chooser.selectDirectory(psidirectory);
    chooser.select(psiclass);
  }
}
