package com.intellij.javaee;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.xml.Html5SchemaProvider;

/**
 * @author Eugene.Kudelevsky
 */
public class DefaultHtmlDoctypeInitialConfigurator {

  public DefaultHtmlDoctypeInitialConfigurator(ProjectManager projectManager,
                                               PropertiesComponent propertiesComponent) {
    if (!propertiesComponent.getBoolean("DefaultHtmlDoctype.MigrateToHtml5", false)) {
      propertiesComponent.setValue("DefaultHtmlDoctype.MigrateToHtml5", Boolean.TRUE.toString());
      ExternalResourceManagerEx.getInstanceEx()
        .setDefaultHtmlDoctype(Html5SchemaProvider.getHtml5SchemaLocation(), projectManager.getDefaultProject());
    }
  }
}
