/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.codehaus.plexus.archiver.jar.Manifest;
import org.codehaus.plexus.archiver.jar.ManifestException;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.importing.ManifestImporter;
import org.jetbrains.idea.maven.project.MavenProject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.Attributes;

import static org.codehaus.plexus.archiver.jar.Manifest.Attribute;

/**
 * @author Vladislav.Soroka
 * @since 5/22/2014
 */
public class ManifestBuilder {

  private static final Map<String, String> PACKAGING_PLUGINS = ContainerUtil.newHashMap(
    Pair.create("jar", "maven-jar-plugin"),
    Pair.create("ejb", "maven-ejb-plugin"),
    Pair.create("ejb-client", "maven-ejb-plugin"),
    Pair.create("war", "maven-war-plugin"),
    Pair.create("ear", "maven-ear-plugin")
  );

  @NotNull private final MavenProject myMavenProject;
  @Nullable private String myJdkVersion;

  public ManifestBuilder(@NotNull MavenProject mavenProject) {
    myMavenProject = mavenProject;
  }

  public ManifestBuilder withJdkVersion(String jdkVersion) {
    myJdkVersion = jdkVersion;
    return this;
  }

  @NotNull
  public java.util.jar.Manifest build() throws ManifestBuilderException {
    try {
      Element mavenPackagingPluginConfiguration = getMavenPackagingPluginConfiguration(myMavenProject);
      final Element mavenArchiveConfiguration =
        mavenPackagingPluginConfiguration != null ? mavenPackagingPluginConfiguration.getChild("archive") : null;

      if (mavenArchiveConfiguration == null) return getDefaultManifest(Collections.<String, String>emptyMap());

      final Element manifestEntries = mavenArchiveConfiguration.getChild("manifestEntries");
      Map<String, String> entries = getManifestEntries(manifestEntries);

      final Element manifestConfiguration = mavenArchiveConfiguration.getChild("manifest");
      final Manifest configuredManifest = getConfiguredManifest(myMavenProject, manifestConfiguration, entries);

      if (!entries.isEmpty()) {
        addManifestEntries(configuredManifest, entries);
      }

      addCustomManifestSections(configuredManifest, mavenArchiveConfiguration);

      Manifest finalManifest = getDefaultManifest(entries);
      // merge configured manifest
      merge(finalManifest, configuredManifest);

      // merge user supplied manifest
      final Manifest userSuppliedManifest = getUserSuppliedManifest(mavenArchiveConfiguration);
      merge(finalManifest, userSuppliedManifest);
      return finalManifest;
    }
    catch (ManifestException e) {
      throw new ManifestBuilderException(e);
    }
  }

  @NotNull
  public static String getClasspath(@NotNull MavenProject mavenProject) {
    Element mavenPackagingPluginConfiguration = getMavenPackagingPluginConfiguration(mavenProject);
    final Element mavenArchiveConfiguration =
      mavenPackagingPluginConfiguration != null ? mavenPackagingPluginConfiguration.getChild("archive") : null;
    final Element manifestConfiguration = mavenArchiveConfiguration != null ? mavenArchiveConfiguration.getChild("manifest") : null;
    final ManifestImporter manifestImporter = ManifestImporter.getManifestImporter(mavenProject.getPackaging());
    return manifestImporter.getClasspath(mavenProject, manifestConfiguration);
  }

  @NotNull
  public static String getClasspathPrefix(@Nullable Element manifestConfiguration) {
    String classpathPrefix = MavenJDOMUtil.findChildValueByPath(manifestConfiguration, "classpathPrefix", "").replaceAll("\\\\", "/");
    if (classpathPrefix.length() != 0 && !classpathPrefix.endsWith("/")) {
      classpathPrefix += "/";
    }
    return classpathPrefix;
  }

  @Nullable
  private static Element getMavenPackagingPluginConfiguration(@NotNull MavenProject mavenProject) {
    Element mavenPackagingPluginConfiguration = null;
    final String packaging = mavenProject.getPackaging();
    if (StringUtil.isEmpty(packaging)) {
      mavenPackagingPluginConfiguration = mavenProject.getPluginConfiguration("org.apache.maven.plugins", "maven-jar-plugin");
    }
    else {
      final String pluginArtifactId = PACKAGING_PLUGINS.get(StringUtil.toLowerCase(packaging));
      if (pluginArtifactId != null) {
        mavenPackagingPluginConfiguration = mavenProject.getPluginConfiguration("org.apache.maven.plugins", pluginArtifactId);
      }
    }
    return mavenPackagingPluginConfiguration;
  }


  private static Map<String, String> getManifestEntries(Element manifestEntries) {
    boolean hasManifestEntries = manifestEntries != null && manifestEntries.getContentSize() > 0;
    Map<String, String> entries = hasManifestEntries ?
                                  new LinkedHashMap<>(manifestEntries.getContentSize()) :
                                  Collections.<String, String>emptyMap();
    if (hasManifestEntries) {
      for (Element element : manifestEntries.getChildren()) {
        entries.put(element.getName(), element.getTextTrim());
      }
    }
    return entries;
  }

  private static void addCustomManifestSections(@NotNull Manifest manifest, @NotNull Element mavenArchiveConfiguration)
    throws ManifestException {

    for (Element section : MavenJDOMUtil.findChildrenByPath(mavenArchiveConfiguration, "manifestSections", "manifestSection")) {
      Manifest.Section theSection = new Manifest.Section();

      final String sectionName = MavenJDOMUtil.findChildValueByPath(section, "name");
      theSection.setName(sectionName);

      final Element manifestEntries = section.getChild("manifestEntries");
      Map<String, String> entries = getManifestEntries(manifestEntries);

      if (!entries.isEmpty()) {
        for (Map.Entry<String, String> entry : entries.entrySet()) {
          Attribute attr = new Attribute(entry.getKey(), entry.getValue());
          theSection.addConfiguredAttribute(attr);
        }
      }
      manifest.addConfiguredSection(theSection);
    }
  }

  @NotNull
  private Manifest getDefaultManifest(@NotNull Map<String, String> entries) throws ManifestException {
    Manifest finalManifest = new Manifest();
    addManifestAttribute(finalManifest, entries, "Created-By", ApplicationNamesInfo.getInstance().getFullProductName());
    addManifestAttribute(finalManifest, entries, "Built-By", System.getProperty("user.name"));
    if (!StringUtil.isEmpty(myJdkVersion)) {
      addManifestAttribute(finalManifest, entries, "Build-Jdk", myJdkVersion);
    }
    return finalManifest;
  }

  private static void addManifestEntries(@NotNull Manifest manifest, @NotNull Map<String, String> entries)
    throws ManifestException {
    if (!entries.isEmpty()) {
      for (Map.Entry<String, String> entry : entries.entrySet()) {
        Attribute attr = manifest.getMainSection().getAttribute(entry.getKey());
        if ("Class-Path".equals(entry.getKey()) && attr != null) {
          // Merge the user-supplied Class-Path value with the programmatically
          // generated Class-Path.  Note that the user-supplied value goes first
          // so that resources there will override any in the standard Class-Path.
          attr.setValue(entry.getValue() + " " + attr.getValue());
        }
        else {
          addManifestAttribute(manifest, entry.getKey(), entry.getValue());
        }
      }
    }
  }

  @Nullable
  private Manifest getUserSuppliedManifest(@Nullable Element mavenArchiveConfiguration) {
    Manifest manifest = null;
    String manifestPath = MavenJDOMUtil.findChildValueByPath(mavenArchiveConfiguration, "manifestFile");
    if (manifestPath != null) {
      File manifestFile = new File(manifestPath);
      if (!manifestFile.isAbsolute()) {
        manifestFile = new File(myMavenProject.getDirectory(), manifestPath);
      }
      if (manifestFile.isFile()) {
        FileInputStream fis = null;
        try {
          //noinspection IOResourceOpenedButNotSafelyClosed
          fis = new FileInputStream(manifestFile);
          manifest = new Manifest(fis);
        }
        catch (IOException ignore) {
        }
        finally {
          StreamUtil.closeStream(fis);
        }
      }
    }

    return manifest;
  }

  @NotNull
  private static Manifest getConfiguredManifest(@NotNull MavenProject mavenProject,
                                                @Nullable Element manifestConfiguration,
                                                @NotNull Map<String, String> entries) throws ManifestException {
    final Manifest manifest = new Manifest();

    boolean isAddDefaultSpecificationEntries =
      Boolean.valueOf(MavenJDOMUtil.findChildValueByPath(manifestConfiguration, "addDefaultSpecificationEntries", "false"));
    if (isAddDefaultSpecificationEntries) {
      addManifestAttribute(manifest, entries, "Specification-Title", mavenProject.getName());
      addManifestAttribute(manifest, entries, "Specification-Version", mavenProject.getMavenId().getVersion());
    }

    boolean isAddDefaultImplementationEntries =
      Boolean.valueOf(MavenJDOMUtil.findChildValueByPath(manifestConfiguration, "addDefaultImplementationEntries", "false"));
    if (isAddDefaultImplementationEntries) {
      addManifestAttribute(manifest, entries, "Implementation-Title", mavenProject.getName());
      addManifestAttribute(manifest, entries, "Implementation-Version", mavenProject.getMavenId().getVersion());
      addManifestAttribute(manifest, entries, "Implementation-Vendor-Id", mavenProject.getMavenId().getGroupId());
    }

    String packageName = MavenJDOMUtil.findChildValueByPath(manifestConfiguration, "packageName");
    if (packageName != null) {
      addManifestAttribute(manifest, entries, "Package", packageName);
    }

    String mainClass = MavenJDOMUtil.findChildValueByPath(manifestConfiguration, "mainClass");
    if (!StringUtil.isEmpty(mainClass)) {
      addManifestAttribute(manifest, entries, "Main-Class", mainClass);
    }

    boolean isAddClasspath = Boolean.valueOf(MavenJDOMUtil.findChildValueByPath(manifestConfiguration, "addClasspath", "false"));
    if (isAddClasspath) {
      final ManifestImporter manifestImporter = ManifestImporter.getManifestImporter(mavenProject.getPackaging());
      String classpath = manifestImporter.getClasspath(mavenProject, manifestConfiguration);
      if (!classpath.isEmpty()) {
        addManifestAttribute(manifest, "Class-Path", classpath);
      }
    }
    return manifest;
  }

  private static void addManifestAttribute(@NotNull Manifest manifest, @NotNull Map<String, String> map, String key, String value)
    throws ManifestException {
    if (map.containsKey(key)) return;
    addManifestAttribute(manifest, key, value);
  }

  private static void addManifestAttribute(@NotNull Manifest manifest, String key, String value) throws ManifestException {
    if (!StringUtil.isEmpty(value)) {
      Attribute attr = new Attribute(key, value);
      manifest.addConfiguredAttribute(attr);
    }
  }

  private static void merge(@NotNull java.util.jar.Manifest target, @Nullable java.util.jar.Manifest other) {
    if (other != null) {
      mergeAttributes(target.getMainAttributes(), other.getMainAttributes());

      for (Map.Entry<String, Attributes> o : other.getEntries().entrySet()) {
        Attributes ourSection = target.getAttributes(o.getKey());
        Attributes otherSection = o.getValue();
        if (ourSection == null) {
          if (otherSection != null) {
            target.getEntries().put(o.getKey(), (Attributes)otherSection.clone());
          }
        }
        else {
          mergeAttributes(ourSection, otherSection);
        }
      }
    }
  }

  private static void mergeAttributes(@NotNull Attributes target, @NotNull Attributes section) {
    for (Object o : section.keySet()) {
      Attributes.Name key = (Attributes.Name)o;
      final Object value = section.get(o);
      target.put(key, value);
    }
  }


  public static class ManifestBuilderException extends Exception {
    public ManifestBuilderException(Throwable cause) {
      super(cause);
    }
  }
}
