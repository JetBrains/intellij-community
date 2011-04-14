package org.jetbrains.idea.maven.project;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.project.Project;

public interface AdditionalMavenImportingSettings {

  ExtensionPointName<AdditionalMavenImportingSettings> EP_NAME =
    ExtensionPointName.create("org.jetbrains.idea.maven.additional.importing.settings");

  UnnamedConfigurable createConfigurable(Project project);
}
