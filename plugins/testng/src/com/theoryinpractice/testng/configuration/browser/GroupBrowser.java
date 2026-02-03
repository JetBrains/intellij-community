// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.theoryinpractice.testng.configuration.browser;

import com.intellij.execution.configuration.BrowseModuleValueActionListener;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.theoryinpractice.testng.TestngBundle;
import com.theoryinpractice.testng.configuration.TestNGConfigurationEditor;
import com.theoryinpractice.testng.model.TestClassFilter;
import com.theoryinpractice.testng.util.TestNGUtil;
import org.jetbrains.annotations.Nullable;

/**
 * @author Hani Suleiman
 */
public class GroupBrowser extends BrowseModuleValueActionListener
{
    private final TestNGConfigurationEditor editor;

    public GroupBrowser(Project project, TestNGConfigurationEditor editor) {
        super(project);
        this.editor = editor;
    }

    @Override
    protected @Nullable String showDialog() {
      TestClassFilter filter;
      Module module = editor.getModuleSelector().getModule();
      if (module == null) {
        filter = new TestClassFilter(GlobalSearchScope.projectScope(getProject()), getProject(), false);
      }
      else {
        filter = new TestClassFilter(GlobalSearchScope.moduleScope(module), getProject(), false);
      }
      PsiClass[] classes = TestNGUtil.getAllTestClasses(filter, true);
      if(classes == null || classes.length == 0) {
        Messages.showMessageDialog(getField(), TestngBundle.message("testng.group.browser.no.tests.found.in.project"), TestngBundle.message("testng.group.browser.cannot.browse.groups"), Messages.getInformationIcon());
        return null;
      } else {
        return GroupList.showDialog(classes, getField());
      }
    }

}
