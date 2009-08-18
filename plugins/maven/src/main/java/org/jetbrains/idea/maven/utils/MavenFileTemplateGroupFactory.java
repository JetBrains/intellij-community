package org.jetbrains.idea.maven.utils;

import com.intellij.ide.fileTemplates.FileTemplateDescriptor;
import com.intellij.ide.fileTemplates.FileTemplateGroupDescriptor;
import com.intellij.ide.fileTemplates.FileTemplateGroupDescriptorFactory;

public class MavenFileTemplateGroupFactory implements FileTemplateGroupDescriptorFactory {
  public static final String MAVEN_PROJECT_XML_TEMPLATE = "Maven Project.xml";
  public static final String MAVEN_PROFILES_XML_TEMPLATE = "Maven Profiles.xml";
  public static final String MAVEN_SETTINGS_XML_TEMPLATE = "Maven Settings.xml";

  public FileTemplateGroupDescriptor getFileTemplatesDescriptor() {
    FileTemplateGroupDescriptor group = new FileTemplateGroupDescriptor("Maven", MavenIcons.MAVEN_ICON);

    group.addTemplate(new FileTemplateDescriptor(MAVEN_PROJECT_XML_TEMPLATE, MavenIcons.MAVEN_ICON));
    group.addTemplate(new FileTemplateDescriptor(MAVEN_PROFILES_XML_TEMPLATE, MavenIcons.MAVEN_ICON));
    group.addTemplate(new FileTemplateDescriptor(MAVEN_SETTINGS_XML_TEMPLATE, MavenIcons.MAVEN_ICON));

    return group;
  }
}
