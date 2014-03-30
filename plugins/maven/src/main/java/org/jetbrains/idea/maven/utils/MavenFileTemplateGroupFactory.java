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
package org.jetbrains.idea.maven.utils;

import com.intellij.ide.fileTemplates.FileTemplateDescriptor;
import com.intellij.ide.fileTemplates.FileTemplateGroupDescriptor;
import com.intellij.ide.fileTemplates.FileTemplateGroupDescriptorFactory;
import icons.MavenIcons;

public class MavenFileTemplateGroupFactory implements FileTemplateGroupDescriptorFactory {
  public static final String MAVEN_PROJECT_XML_TEMPLATE = "Maven Project.xml";
  public static final String MAVEN_PROFILES_XML_TEMPLATE = "Maven Profiles.xml";
  public static final String MAVEN_SETTINGS_XML_TEMPLATE = "Maven Settings.xml";

  public FileTemplateGroupDescriptor getFileTemplatesDescriptor() {
    FileTemplateGroupDescriptor group = new FileTemplateGroupDescriptor("Maven", MavenIcons.MavenLogo);

    group.addTemplate(new FileTemplateDescriptor(MAVEN_PROJECT_XML_TEMPLATE, MavenIcons.MavenLogo));
    group.addTemplate(new FileTemplateDescriptor(MAVEN_PROFILES_XML_TEMPLATE, MavenIcons.MavenLogo));
    group.addTemplate(new FileTemplateDescriptor(MAVEN_SETTINGS_XML_TEMPLATE, MavenIcons.MavenLogo));

    return group;
  }
}
