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
package org.jetbrains.idea.maven.dom;

import com.intellij.openapi.module.Module;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.model.MavenDomProfile;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.dom.model.MavenDomProperties;
import org.jetbrains.idea.maven.dom.references.MavenFilteredPropertyPsiReferenceProvider;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.server.MavenServerUtil;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MavenPropertyResolver {
  public static final Pattern PATTERN = Pattern.compile("\\$\\{(.+?)\\}|@(.+?)@");

  public static void doFilterText(Module module,
                                  String text,
                                  Properties additionalProperties,
                                  String propertyEscapeString,
                                  String escapedCharacters,
                                  Appendable out) throws IOException {
    MavenProjectsManager manager = MavenProjectsManager.getInstance(module.getProject());
    MavenProject mavenProject = manager.findProject(module);
    if (mavenProject == null) {
      out.append(text);
      return;
    }
    
    doFilterText(MavenFilteredPropertyPsiReferenceProvider.getDelimitersPattern(mavenProject),
                 mavenProject,
                 text,
                 additionalProperties,
                 propertyEscapeString,
                 escapedCharacters,
                 null,
                 out);
  }
  
  private static void doFilterText(Pattern pattern,
                                   MavenProject mavenProject,
                                   String text,
                                   Properties additionalProperties,
                                   @Nullable String escapeString,
                                   @Nullable String escapedCharacters,
                                   @Nullable Map<String, String> resolvedPropertiesParam,
                                   Appendable out) throws IOException {
    Map<String, String> resolvedProperties = resolvedPropertiesParam;
    
    Matcher matcher = pattern.matcher(text);
    int groupCount = matcher.groupCount();
    
    int last = 0;
    while (matcher.find()) {
      if (escapeString != null) {
        int escapeStringStartIndex = matcher.start() - escapeString.length();
        if (escapeStringStartIndex >= last) {
          if (text.startsWith(escapeString, escapeStringStartIndex)) {
            out.append(text, last, escapeStringStartIndex);
            out.append(matcher.group());
            last = matcher.end();
            continue;
          }
        }
      }

      out.append(text, last, matcher.start());
      last = matcher.end();

      String propertyName = null;

      for (int i = 0; i < groupCount; i++) {
        propertyName = matcher.group(i + 1);
        if (propertyName != null) {
          break;
        }
      }

      assert propertyName != null;

      if (resolvedProperties == null) {
        resolvedProperties = new HashMap<String, String>();
      }
      
      String propertyValue = resolvedProperties.get(propertyName);
      if (propertyValue == null) {
        if (resolvedProperties.containsKey(propertyName)) { // if cyclic property dependencies
          out.append(matcher.group());
          continue;
        }

        String resolved = doResolveProperty(propertyName, mavenProject, additionalProperties);
        if (resolved == null) {
          out.append(matcher.group());
          continue;
        }

        resolvedProperties.put(propertyName, null);

        StringBuilder sb = new StringBuilder();
        doFilterText(pattern, mavenProject, resolved, additionalProperties, null, null, resolvedProperties, sb);
        propertyValue = sb.toString();

        resolvedProperties.put(propertyName, propertyValue);
      }

      if (escapedCharacters == null) {
        out.append(propertyValue);
      }
      else {
        for (int i = 0; i < propertyValue.length(); i++) {
          char ch = propertyValue.charAt(i);
          if (escapedCharacters.indexOf(ch) != -1) {
            out.append('\\');
          }
          out.append(ch);
        }
      }
    }
    
    out.append(text, last, text.length());
  }

  public static String resolve(String text, MavenDomProjectModel projectDom) {
    MavenProject mavenProject = MavenDomUtil.findProject(projectDom);
    if (mavenProject == null) return text;
    
    StringBuilder res = new StringBuilder();
    try {
      doFilterText(PATTERN, mavenProject, text, collectPropertiesFromDOM(mavenProject, projectDom), null, null, null, res);
    }
    catch (IOException e) {
      throw new RuntimeException(e); // never thrown
    }

    return res.toString();
  }

  private static Properties collectPropertiesFromDOM(MavenProject project, MavenDomProjectModel projectDom) {
    Properties result = new Properties();

    collectPropertiesFromDOM(projectDom.getProperties(), result);

    Collection<String> activePropfiles = project.getActivatedProfilesIds();
    for (MavenDomProfile each : projectDom.getProfiles().getProfiles()) {
      XmlTag idTag = each.getId().getXmlTag();
      if (idTag == null || !activePropfiles.contains(idTag.getValue().getTrimmedText())) continue;
      collectPropertiesFromDOM(each.getProperties(), result);
    }

    return result;
  }

  private static void collectPropertiesFromDOM(MavenDomProperties props, Properties result) {
    XmlTag propsTag = props.getXmlTag();
    if (propsTag != null) {
      for (XmlTag each : propsTag.getSubTags()) {
        result.setProperty(each.getName(), each.getValue().getTrimmedText());
      }
    }
  }

  @Nullable
  private static String doResolveProperty(String propName, MavenProject project, Properties additionalProperties) {
    String result;

    result = MavenServerUtil.collectSystemProperties().getProperty(propName);
    if (result != null) return result;

    if (propName.startsWith("pom.")) {
      propName = propName.substring("pom.".length());
    }
    else if (propName.startsWith("project.")) {
      propName = propName.substring("project.".length());
    }

    if (propName.equals("basedir")) return project.getDirectory();

    result = project.getModelMap().get(propName);
    if (result != null) return result;

    result = additionalProperties.getProperty(propName);
    if (result != null) return result;

    result = project.getProperties().getProperty(propName);
    if (result != null) return result;

    return null;
  }
}
