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
package org.jetbrains.idea.maven.plugins.api;

import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.Pair;
import com.intellij.util.xml.Required;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;

/**
 * @author Sergey Evdokimov
 */
public class MavenPluginDescriptor extends AbstractExtensionPointBean {

  public static final ExtensionPointName<MavenPluginDescriptor> EP_NAME = new ExtensionPointName<MavenPluginDescriptor>("org.jetbrains.idea.maven.pluginDescriptor");

  @Attribute("mavenId")
  @Required
  public String mavenId;

  @Property(surroundWithTag = false)
  @AbstractCollection(surroundWithTag = false)
  public Param[] params;

  @Property(surroundWithTag = false)
  @AbstractCollection(surroundWithTag = false)
  public ModelProperty[] properties;

  @Tag("property")
  public static class ModelProperty {
    @Attribute("name")
    @Required
    public String name;
  }

  @Tag("param")
  public static class Param {

    @Attribute("name")
    @Required
    public String name;

    @Attribute("goal")
    public String goal;

    /**
     * Class name of reference provider. The reference provider must implement MavenParamReferenceProvider or PsiReferenceProvider.
     */
    @Attribute("refProvider")
    public String refProvider;

    @Attribute("values")
    public String values;

    @Attribute("soft")
    public Boolean soft;

    /**
     * Disallow to add standard maven references to parameter like <delimiter>$$</delimiter>, see MavenPropertyPsiReferenceProvider
     */
    @Attribute("disableReferences")
    public Boolean disableReferences;

    /**
     * Language to inject.
     */
    @Attribute("language")
    public String language;

    /**
     * Class of type org.jetbrains.idea.maven.plugins.api.MavenParamLanguageProvider
     */
    @Attribute("languageProvider")
    public String languageProvider;

    @Attribute("languageInjectionPrefix")
    public String languageInjectionPrefix;

    @Attribute("languageInjectionSuffix")
    public String languageInjectionSuffix;
  }

  public static Pair<String, String> parsePluginId(String mavenId) {
    int idx = mavenId.indexOf(':');
    if (idx <= 0 || idx == mavenId.length() - 1 || mavenId.lastIndexOf(':') != idx) {
      throw new RuntimeException("Failed to parse mavenId: " + mavenId + " (mavenId should has format 'groupId:artifactId')");
    }

    return new Pair<String, String>(mavenId.substring(0, idx), mavenId.substring(idx + 1));
  }

}
