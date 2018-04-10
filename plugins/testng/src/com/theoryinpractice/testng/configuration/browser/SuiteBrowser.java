/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.execution.configuration.BrowseModuleValueActionListener;

/**
 * @author Hani Suleiman
 */
public class SuiteBrowser extends BrowseModuleValueActionListener
{
    public SuiteBrowser(Project project) {
        super(project);
    }

    @Override
    public String showDialog() {
        FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, false, false, false, false) {
          @Override
          public boolean isFileVisible(VirtualFile virtualFile, boolean showHidden) {
                if(!showHidden && virtualFile.getName().charAt(0) == '.') return false;
                return virtualFile.isDirectory() || "xml".equals(virtualFile.getExtension());
            }
        };
        descriptor.setDescription("Please select the testng.xml suite file");
        descriptor.setTitle("Select Suite");
      VirtualFile file = FileChooser.chooseFile(descriptor, getProject(), null);
        return file != null ? file.getPath() : null;
    }

}
