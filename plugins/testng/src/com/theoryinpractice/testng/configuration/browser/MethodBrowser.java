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

import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.execution.configuration.BrowseModuleValueActionListener;
import com.theoryinpractice.testng.configuration.TestNGConfigurationEditor;

/**
 * @author Hani Suleiman
 *         Date: Jul 21, 2005
 *         Time: 4:29:00 PM
 */
public class MethodBrowser extends BrowseModuleValueActionListener
{
    private final TestNGConfigurationEditor editor;

    public MethodBrowser(Project project, TestNGConfigurationEditor editor) {
        super(project);
        this.editor = editor;
    }

    @Override
    protected String showDialog() {
        String className = editor.getClassName();
        if(className.trim().length() == 0) {
            Messages.showMessageDialog(getField(), "Set class name first", "Cannot Browse Methods", Messages.getInformationIcon());
            return null;
        }
        PsiClass psiclass = editor.getModuleSelector().findClass(className);
        if(psiclass == null) {
            Messages.showMessageDialog(getField(), "Class " + className + " does not exist", "Cannot Browse Methods", Messages.getInformationIcon());
            return null;
        } else {
            PsiMethod psimethod = MethodList.showDialog(psiclass, getField());
            return psimethod == null ? null : psimethod.getName();
        }
    }

}
