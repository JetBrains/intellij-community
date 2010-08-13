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

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.util.xml.ElementPresentationManager;
import com.intellij.util.xml.TypeNameManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenDomBundle;
import org.jetbrains.idea.maven.dom.model.MavenDomDependency;
import org.jetbrains.idea.maven.dom.model.MavenDomPlugin;
import org.jetbrains.idea.maven.dom.model.MavenDomRepository;
import org.jetbrains.idea.maven.model.MavenConstants;

import java.io.File;

public class MavenEnvironmentRegistrar implements ApplicationComponent {
  private static final String MAVEN_REPOSITORY = "MAVEN_REPOSITORY";

  @NotNull
  public String getComponentName() {
    return MavenEnvironmentRegistrar.class.getName();
  }

  public void initComponent() {
    registerFileTypes();
    registerPathVariable();
    registerPresentations();
  }

  private void registerFileTypes() {
    // we should not change file types in unit test mode
    if (ApplicationManager.getApplication().isUnitTestMode()) return;
    FileTypeManager.getInstance().associateExtension(XmlFileType.INSTANCE, MavenConstants.POM_EXTENSION);
  }

  private void registerPathVariable() {
    File repository = MavenUtil.resolveLocalRepository(null, null, null);
    PathMacros macros = PathMacros.getInstance();

    for (String each : macros.getAllMacroNames()) {
      String path = macros.getValue(each);
      if (path == null) continue;
      if (new File(path).equals(repository)) return;
    }
    macros.setMacro(MAVEN_REPOSITORY, repository.getPath());
  }

  private void registerPresentations() {
    TypeNameManager.registerTypeName(MavenDomRepository.class, MavenDomBundle.message("maven.repository"));
    TypeNameManager.registerTypeName(MavenDomPlugin.class, MavenDomBundle.message("maven.plugin"));
    TypeNameManager.registerTypeName(MavenDomDependency.class, MavenDomBundle.message("maven.dependency"));

    ElementPresentationManager.registerIcon(MavenDomRepository.class, MavenIcons.REPOSITORY_ICON);
    ElementPresentationManager.registerIcon(MavenDomDependency.class, MavenIcons.DEPENDENCY_ICON);
    ElementPresentationManager.registerIcon(MavenDomPlugin.class, MavenIcons.PLUGIN_ICON);
  }

  public void disposeComponent() {
  }
}
