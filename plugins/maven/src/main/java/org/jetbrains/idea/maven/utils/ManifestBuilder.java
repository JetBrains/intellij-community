// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.utils;

import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.util.text.StringUtil;
import org.codehaus.plexus.archiver.jar.Manifest;
import org.codehaus.plexus.archiver.jar.ManifestException;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.importing.ManifestImporter;
import org.jetbrains.idea.maven.project.MavenProject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.Attributes;

import static org.codehaus.plexus.archiver.jar.Manifest.Attribute;

/**
 * @author Vladislav.Soroka
 */
public class ManifestBuilder {

  private static final Map<String, String> PACKAGING_PLUGINS = Map.of("jar", "maven-jar-plugin", "ejb", "maven-ejb-plugin", "ejb-client", "maven-ejb-plugin", "war", "maven-war-plugin", "ear", "maven-ear-plugin");

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

      if (mavenArchiveConfiguration == null) return getDefaultManifest(Collections.emptyMap());

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
                                  Collections.emptyMap();
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
    String manifestPath = MavenJDOMUtil.findChildValueByPath(mavenArchiveConfiguration, "manifestFile");
    if (manifestPath != null) {
      Path manifestFile = Path.of(manifestPath);
      if (!manifestFile.isAbsolute()) {
        manifestFile = Path.of(myMavenProject.getDirectory(), manifestPath);
      }
      if (!Files.isDirectory(manifestFile)) {
        try (InputStream fis = Files.newInputStream(manifestFile)) {
          return new Manifest(fis);
        }
        catch (IOException ignore) { }
      }
    }

    return null;
  }

  @NotNull
  private static Manifest getConfiguredManifest(@NotNull MavenProject mavenProject,
                                                @Nullable Element manifestConfiguration,
                                                @NotNull Map<String, String> entries) throws ManifestException {
    final Manifest manifest = new Manifest();

    boolean isAddDefaultSpecificationEntries =
      Boolean.parseBoolean(MavenJDOMUtil.findChildValueByPath(manifestConfiguration, "addDefaultSpecificationEntries", "false"));
    if (isAddDefaultSpecificationEntries) {
      addManifestAttribute(manifest, entries, "Specification-Title", mavenProject.getName());
      addManifestAttribute(manifest, entries, "Specification-Version", mavenProject.getMavenId().getVersion());
    }

    boolean isAddDefaultImplementationEntries =
      Boolean.parseBoolean(MavenJDOMUtil.findChildValueByPath(manifestConfiguration, "addDefaultImplementationEntries", "false"));
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

    boolean isAddClasspath = Boolean.parseBoolean(MavenJDOMUtil.findChildValueByPath(manifestConfiguration, "addClasspath", "false"));
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
