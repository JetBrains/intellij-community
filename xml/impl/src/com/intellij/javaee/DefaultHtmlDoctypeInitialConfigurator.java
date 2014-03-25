package com.intellij.javaee;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.xml.Html5SchemaProvider;

/**
 * @author Eugene.Kudelevsky
 */
public class DefaultHtmlDoctypeInitialConfigurator {
  public static final int VERSION = 1;

  public DefaultHtmlDoctypeInitialConfigurator(ProjectManager projectManager,
                                               PropertiesComponent propertiesComponent) {
    if (!propertiesComponent.getBoolean("DefaultHtmlDoctype.MigrateToHtml5", false)) {
      propertiesComponent.setValue("DefaultHtmlDoctype.MigrateToHtml5", Boolean.TRUE.toString());
      ExternalResourceManagerEx.getInstanceEx()
        .setDefaultHtmlDoctype(Html5SchemaProvider.getHtml5SchemaLocation(), projectManager.getDefaultProject());
    }
    // sometimes VFS fails to pick up updated schema contents and we need to force refresh
    if (propertiesComponent.getOrInitInt("DefaultHtmlDoctype.Refreshed", 0) < VERSION) {
      propertiesComponent.setValue("DefaultHtmlDoctype.Refreshed", Integer.toString(VERSION));
      final String schemaUrl = VfsUtilCore.pathToUrl(Html5SchemaProvider.getHtml5SchemaLocation());
      final VirtualFile schemaFile = VirtualFileManager.getInstance().findFileByUrl(schemaUrl);
      if (schemaFile != null) {
        schemaFile.getParent().refresh(false, true);
      }
    }
  }
}
