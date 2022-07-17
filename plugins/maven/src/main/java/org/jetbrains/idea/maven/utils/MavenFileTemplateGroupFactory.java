// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.utils;

import com.intellij.ide.fileTemplates.FileTemplateDescriptor;
import com.intellij.ide.fileTemplates.FileTemplateGroupDescriptor;
import com.intellij.ide.fileTemplates.FileTemplateGroupDescriptorFactory;

import static icons.OpenapiIcons.RepositoryLibraryLogo;

public class MavenFileTemplateGroupFactory implements FileTemplateGroupDescriptorFactory {
  public static final String MAVEN_PROJECT_XML_TEMPLATE = "Maven Project.xml";
  public static final String MAVEN_PROFILES_XML_TEMPLATE = "Maven Profiles.xml";
  public static final String MAVEN_SETTINGS_XML_TEMPLATE = "Maven Settings.xml";

  @Override
  public FileTemplateGroupDescriptor getFileTemplatesDescriptor() {
    FileTemplateGroupDescriptor group = new FileTemplateGroupDescriptor("Maven", RepositoryLibraryLogo); //NON-NLS

    group.addTemplate(new FileTemplateDescriptor(MAVEN_PROJECT_XML_TEMPLATE, RepositoryLibraryLogo));
    group.addTemplate(new FileTemplateDescriptor(MAVEN_PROFILES_XML_TEMPLATE, RepositoryLibraryLogo));
    group.addTemplate(new FileTemplateDescriptor(MAVEN_SETTINGS_XML_TEMPLATE, RepositoryLibraryLogo));

    return group;
  }
}
