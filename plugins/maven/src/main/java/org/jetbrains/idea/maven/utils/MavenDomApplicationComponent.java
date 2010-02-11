/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.codeInsight.template.impl.TemplateSettings;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.xml.ElementPresentationManager;
import com.intellij.util.xml.TypeNameManager;
import org.jdom.Document;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenDomBundle;
import org.jetbrains.idea.maven.dom.model.MavenDomDependency;
import org.jetbrains.idea.maven.dom.model.MavenDomPlugin;
import org.jetbrains.idea.maven.dom.model.MavenDomRepository;
import org.jetbrains.idea.maven.embedder.MavenEmbedderFactory;

import java.io.File;

public class MavenDomApplicationComponent implements ApplicationComponent {

  private static final Logger LOG = Logger.getInstance(MavenDomApplicationComponent.class.getName());
  @NonNls private static final String LIVE_TEMPLATES_DIR = "/liveTemplates/";
  @NonNls private static final String[] LIVE_TEMPLATES_FILES = {"maven_generate_dom.xml"};


  @NotNull
  public String getComponentName() {
    return MavenDomApplicationComponent.class.getName();
  }

  public void initComponent() {
    registerLiveTemplates();
    registerPresentations();
  }

  private static void registerPresentations() {
    TypeNameManager.registerTypeName(MavenDomRepository.class, MavenDomBundle.message("maven.repository"));
    TypeNameManager.registerTypeName(MavenDomPlugin.class, MavenDomBundle.message("maven.plugin"));
    TypeNameManager.registerTypeName(MavenDomDependency.class, MavenDomBundle.message("maven.dependency"));

    ElementPresentationManager.registerIcon(MavenDomRepository.class, MavenIcons.PLUGIN_ICON);
    ElementPresentationManager.registerIcon(MavenDomDependency.class, MavenIcons.DEPENDENCY_ICON);
    ElementPresentationManager.registerIcon(MavenDomPlugin.class, MavenIcons.PLUGIN_ICON);
  }

  private static void registerLiveTemplates() {
    final TemplateSettings settings = TemplateSettings.getInstance();
    for (String templatesFile : LIVE_TEMPLATES_FILES) {
      try {
        final Document document =
          JDOMUtil.loadDocument(MavenDomApplicationComponent.class.getResourceAsStream(LIVE_TEMPLATES_DIR + templatesFile));
        settings.readHiddenTemplateFile(document);
      }
      catch (Exception e) {
        LOG.error("Can't load " + templatesFile, e);
      }
    }
  }
  public void disposeComponent() {
  }
}