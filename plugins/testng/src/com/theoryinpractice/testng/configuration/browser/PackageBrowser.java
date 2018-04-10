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

import com.intellij.psi.PsiPackage;
import com.intellij.openapi.project.Project;
import com.intellij.execution.configuration.BrowseModuleValueActionListener;
import com.intellij.ide.util.PackageChooserDialog;

/**
 * @author Hani Suleiman
 */
public class PackageBrowser extends BrowseModuleValueActionListener
{
    public PackageBrowser(Project project) {
        super(project);
    }

    @Override
    protected String showDialog() {
        PackageChooserDialog chooser = new PackageChooserDialog("Choose Package", getProject());
        chooser.show();
        PsiPackage psiPackage = chooser.getSelectedPackage();
        String packageName = psiPackage == null ? null : psiPackage.getQualifiedName();
        return packageName;
    }

}
