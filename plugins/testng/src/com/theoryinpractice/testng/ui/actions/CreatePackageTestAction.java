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
package com.theoryinpractice.testng.ui.actions;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.theoryinpractice.testng.configuration.TestNGConfiguration;
import com.theoryinpractice.testng.configuration.TestNGConfigurationType;

/**
 * @author Hani Suleiman Date: Dec 1, 2006 Time: 9:40:24 PM
 */
public class CreatePackageTestAction extends AnAction
{
    private PsiPackage pkg;
    private Module module;

    public void update(AnActionEvent event) {
        pkg = getSelectedPackage(event.getDataContext());
        event.getPresentation().setVisible(pkg != null);
        event.getPresentation().setEnabled(pkg != null);
        if (pkg != null) {
            event.getPresentation().setText("Create \"Tests in '" + pkg.getQualifiedName() + "'\"...");
            module = LangDataKeys.MODULE.getData(event.getDataContext());
        } else {
            module = null;
        }
    }

    public void actionPerformed(AnActionEvent event) {
        RunManager runManager = RunManager.getInstance(pkg.getProject());
        ConfigurationFactory[] factory = ApplicationManager.getApplication().getComponent(TestNGConfigurationType.class).getConfigurationFactories();
        RunnerAndConfigurationSettings settings = runManager.createRunConfiguration("", factory[0]);
        final TestNGConfiguration configuration = (TestNGConfiguration) settings.getConfiguration();
        configuration.setPackageConfiguration(module, pkg);
        settings.setName(configuration.getName());
    }

    PsiPackage getSelectedPackage(DataContext context) {
        final PsiElement element = LangDataKeys.PSI_ELEMENT.getData(context);
        if (element == null) {
            return null;
        }

        if (element instanceof PsiDirectory) {
          return JavaDirectoryService.getInstance().getPackage(((PsiDirectory)element));
        }
        return null;
    }
}
