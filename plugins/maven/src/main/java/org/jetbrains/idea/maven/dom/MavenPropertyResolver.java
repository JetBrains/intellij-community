// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.model.MavenDomParent;
import org.jetbrains.idea.maven.dom.model.MavenDomProfile;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.dom.model.MavenDomProperties;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.server.MavenServerUtil;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MavenPropertyResolver {
  public static final Pattern PATTERN = Pattern.compile("\\$\\{(.+?)}|@(.+?)@");

  /**
   * Resolve properties from the string (either like {@code ${propertyName}} or like {@code @propertyName@}).
   * @param text text string to resolve properties in
   * @param projectDom a project dom
   * @return string with the properties resolved
   */
  public static String resolve(String text, MavenDomProjectModel projectDom) {
    XmlElement element = projectDom.getXmlElement();
    if (element == null) return text;

    VirtualFile file = MavenDomUtil.getVirtualFile(element);
    if (file == null) return text;

    MavenProjectsManager mavenProjectsManager = MavenProjectsManager.getInstance(projectDom.getManager().getProject());

    MavenProject mavenProject = mavenProjectsManager.findProject(file);

    var additionalPropertySource = new AdditionalPropertySourceImpl(mavenProject, projectDom);

    return new MavenPropertyResolverHelper(projectDom, mavenProjectsManager, mavenProject, additionalPropertySource)
      .filterText(text);
  }

  /**
   * @deprecated use {@link MavenPropertyResolver#collectPropertyMapFromDOM(MavenProject, MavenDomProjectModel)} instead
   */
  @Deprecated
  public static Properties collectPropertiesFromDOM(@Nullable MavenProject project, MavenDomProjectModel projectDom) {
    var result = new Properties();

    result.putAll(collectPropertyMapFromDOM(project, projectDom));

    return result;
  }

  public static Map<String, String> collectPropertyMapFromDOM(@Nullable MavenProject project, MavenDomProjectModel projectDom) {
    var result = new HashMap<String, String>();

    collectPropertyMapFromDOM(ReadAction.compute(()->projectDom.getProperties()), result);

    if (project != null) {
      collectPropertiesForActivatedProfiles(project, projectDom, result);
    }
    return result;
  }


  private static void collectPropertiesForActivatedProfiles(@NotNull MavenProject project,
                                                            MavenDomProjectModel projectDom, Map<String, String> result) {
    Collection<String> activeProfiles = project.getActivatedProfilesIds().getEnabledProfiles();
    List<MavenDomProfile> profiles = ReadAction.compute(() -> projectDom.getProfiles().getProfiles());
    for (MavenDomProfile each : profiles) {
      XmlTag idTag = each.getId().getXmlTag();
      if (idTag == null || !activeProfiles.contains(idTag.getValue().getTrimmedText())) continue;
      collectPropertyMapFromDOM(each.getProperties(), result);
    }
  }

  private static void collectPropertyMapFromDOM(MavenDomProperties props, Map<String, String> result) {
    XmlTag propsTag = props.getXmlTag();
    if (propsTag != null) {
      for (XmlTag each : propsTag.getSubTags()) {
        result.put(each.getName(), each.getValue().getTrimmedText());
      }
    }
  }

  interface AdditionalPropertySource {
    String get(String key);
  }

  private static class AdditionalPropertySourceImpl implements AdditionalPropertySource {
    @Nullable
    private final MavenProject mavenProject;
    private final MavenDomProjectModel projectDom;

    private Map<String, String> additionalProperties;

    private AdditionalPropertySourceImpl(@Nullable MavenProject mavenProject, MavenDomProjectModel projectDom) {
      this.mavenProject = mavenProject;
      this.projectDom = projectDom;
    }
    @Override
    public String get(String key) {
      if (null == additionalProperties) {
        additionalProperties = collectPropertyMapFromDOM(mavenProject, projectDom);
      }
      return additionalProperties.get(key);
    }
  }

  private static class MavenPropertyResolverHelper {
    private static final Pattern pattern = PATTERN;
    private final MavenDomProjectModel projectDom;
    private final MavenProjectsManager mavenProjectsManager;
    @Nullable
    private final MavenProject mavenProject;
    private final AdditionalPropertySource additionalPropertySource;

    private MavenPropertyResolverHelper(MavenDomProjectModel projectDom,
                                        MavenProjectsManager mavenProjectsManager,
                                        @Nullable MavenProject mavenProject,
                                        AdditionalPropertySource additionalPropertySource) {
      this.projectDom = projectDom;
      this.mavenProjectsManager = mavenProjectsManager;
      this.mavenProject = mavenProject;
      this.additionalPropertySource = additionalPropertySource;
    }

    public String filterText(String text) {
      StringBuilder res = new StringBuilder();
      try {
        doFilterText(pattern, text, null, res);
      }
      catch (IOException e) {
        throw new RuntimeException(e); // never thrown
      }
      return res.toString();
    }

    private void doFilterText(Pattern pattern,
                              String text,
                              @Nullable Map<String, String> resolvedPropertiesParam,
                              Appendable out) throws IOException {
      Map<String, String> resolvedProperties = resolvedPropertiesParam;

      Matcher matcher = pattern.matcher(text);
      int groupCount = matcher.groupCount();

      int last = 0;
      while (matcher.find()) {
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
          resolvedProperties = new HashMap<>();
        }

        String propertyValue = resolvedProperties.get(propertyName);
        if (propertyValue == null) {
          if (resolvedProperties.containsKey(propertyName)) { // if cyclic property dependencies
            out.append(matcher.group());
            continue;
          }


          String resolved;
          if (mavenProject != null) {
            resolved = doResolvePropertyForMavenProject(propertyName);
          }
          else {
            resolved = doResolvePropertyForMavenDomModel(propertyName);
          }

          if (resolved == null) {
            out.append(matcher.group());
            continue;
          }

          resolvedProperties.put(propertyName, null);

          StringBuilder sb = new StringBuilder();
          doFilterText(pattern, resolved, resolvedProperties, sb);
          propertyValue = sb.toString();

          resolvedProperties.put(propertyName, propertyValue);
        }

        out.append(propertyValue);
      }

      out.append(text, last, text.length());
    }

    @Nullable
    private String doResolvePropertyForMavenProject(String propName) {
      boolean hasPrefix = false;
      String unprefixed = propName;

      if (propName.startsWith("pom.")) {
        unprefixed = propName.substring("pom.".length());
        hasPrefix = true;
      }
      else if (propName.startsWith("project.")) {
        unprefixed = propName.substring("project.".length());
        hasPrefix = true;
      }

      MavenProject selectedProject = mavenProject;

      while (unprefixed.startsWith("parent.")) {
        if (selectedProject == null) return null;
        MavenId parentId = selectedProject.getParentId();
        if (parentId == null) return null;

        unprefixed = unprefixed.substring("parent.".length());

        if (unprefixed.equals("groupId")) {
          return parentId.getGroupId();
        }
        if (unprefixed.equals("artifactId")) {
          return parentId.getArtifactId();
        }
        if (unprefixed.equals("version")) {
          return parentId.getVersion();
        }

        selectedProject = mavenProjectsManager.findProject(parentId);
        if (selectedProject == null) return null;
      }

      if (unprefixed.equals("basedir") || (hasPrefix && mavenProject == selectedProject && unprefixed.equals("baseUri"))) {
        return null == selectedProject ? null : selectedProject.getDirectory();
      }

      if ("java.home".equals(propName) && null != mavenProject) {
        String jreDir = MavenUtil.getModuleJreHome(mavenProjectsManager, mavenProject);
        if (jreDir != null) {
          return jreDir;
        }
      }

      if ("java.version".equals(propName) && null != mavenProject) {
        String javaVersion = MavenUtil.getModuleJavaVersion(mavenProjectsManager, mavenProject);
        if (javaVersion != null) {
          return javaVersion;
        }
      }

      String result;

      result = MavenUtil.getPropertiesFromMavenOpts().get(propName);
      if (result != null) return result;

      if (null == mavenProject) return null;

      result = mavenProject.getMavenConfig().get(propName);
      if (result != null) return result;

      result = mavenProject.getJvmConfig().get(propName);
      if (result != null) return result;

      result = MavenServerUtil.collectSystemProperties().getProperty(propName);
      if (result != null) return result;

      result = selectedProject.getModelMap().get(unprefixed);
      if (result != null) return result;

      result = additionalPropertySource.get(propName);
      if (result != null) return result;

      result = mavenProject.getProperties().getProperty(propName);
      if (result != null) return result;

      if ("settings.localRepository".equals(propName)) {
        return mavenProject.getLocalRepositoryPath().toAbsolutePath().toString();
      }

      return null;
    }

    @Nullable
    private String doResolvePropertyForMavenDomModel(String propName) {
      if (propName.startsWith("parent.")) {
        MavenDomParent parentDomElement = projectDom.getMavenParent();
        if (!parentDomElement.exists()) {
          return null;
        }
        MavenId parentId = new MavenId(parentDomElement.getGroupId().getStringValue(), parentDomElement.getArtifactId().getStringValue(),
                                       parentDomElement.getVersion().getStringValue());

        propName = propName.substring("parent.".length());

        if (propName.equals("groupId")) {
          return parentId.getGroupId();
        }
        if (propName.equals("artifactId")) {
          return parentId.getArtifactId();
        }
        if (propName.equals("version")) {
          return parentId.getVersion();
        }
        return null;
      }


      String result;

      result = MavenUtil.getPropertiesFromMavenOpts().get(propName);
      if (result != null) return result;

      result = MavenServerUtil.collectSystemProperties().getProperty(propName);
      if (result != null) return result;

      result = additionalPropertySource.get(propName);
      if (result != null) return result;


      if ("settings.localRepository".equals(propName)) {
        return MavenProjectsManager.getInstance(projectDom.getManager().getProject()).getRepositoryPath().toAbsolutePath().toString();
      }

      return null;
    }
  }
}
