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

  /**
   * @author Sergey Evdokimov
   */
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

  }
}
