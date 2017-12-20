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
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
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

    @Nullable
    @Override
    protected String showDialog() {
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
        Messages.showMessageDialog(getField(), "No tests found in project", "Cannot Browse Groups", Messages.getInformationIcon());
        return null;
      } else {
        return GroupList.showDialog(classes, getField());
      }
    }

}
