/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.javaee;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.xml.Html5SchemaProvider;

/**
 * @author Eugene.Kudelevsky
 */
public class DefaultHtmlDoctypeInitialConfigurator {
  public static final int VERSION = 3;

  public DefaultHtmlDoctypeInitialConfigurator(ProjectManager projectManager,
                                               PropertiesComponent propertiesComponent) {
    if (!propertiesComponent.getBoolean("DefaultHtmlDoctype.MigrateToHtml5")) {
      propertiesComponent.setValue("DefaultHtmlDoctype.MigrateToHtml5", true);
      ExternalResourceManagerEx.getInstanceEx()
        .setDefaultHtmlDoctype(Html5SchemaProvider.getHtml5SchemaLocation(), projectManager.getDefaultProject());
    }
    // sometimes VFS fails to pick up updated schema contents and we need to force refresh
    if (StringUtilRt.parseInt(propertiesComponent.getValue("DefaultHtmlDoctype.Refreshed"), 0) < VERSION) {
      propertiesComponent.setValue("DefaultHtmlDoctype.Refreshed", Integer.toString(VERSION));
      final String schemaUrl = VfsUtilCore.pathToUrl(Html5SchemaProvider.getHtml5SchemaLocation());
      final VirtualFile schemaFile = VirtualFileManager.getInstance().findFileByUrl(schemaUrl);
      if (schemaFile != null) {
        schemaFile.getParent().refresh(false, true);
      }
    }
  }
}
