// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.model;

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class MavenSource implements Serializable {
  public static final String MAIN_SCOPE = "main";
  public static final String TEST_SCOPE = "test";
  public static final String JAVA_LANG = "java";
  public static final String RESOURCES_LANG = "resources";

  private final @NotNull String myDirectoryAbsolutePath;
  private final ArrayList<String> myIncludes;
  private final ArrayList<String> myExcludes;
  private final String myScope;
  private final String myLang;
  private final String myTargetPath;
  private final String myTargetVersion;
  private final boolean myFiltered;
  private final boolean myEnabled;
  private final boolean myIsSourceTag;


  private MavenSource(boolean isSourceTag,
                      @NotNull String directoryAbsolutePath,
                      List<String> includes,
                      List<String> excludes,
                      String scope,
                      String lang,
                      String targetPath,
                      String targetVersion,
                      boolean filtered,
                      boolean enabled) {

    myIsSourceTag = isSourceTag;
    myDirectoryAbsolutePath = directoryAbsolutePath;
    myIncludes = new ArrayList<>(includes);
    myExcludes = new ArrayList<>(excludes);
    myScope = scope;
    myLang = lang;
    myTargetPath = targetPath;
    myTargetVersion = targetVersion;
    myFiltered = filtered;
    myEnabled = enabled;
  }


  public @NotNull String getDirectory() {
    return myDirectoryAbsolutePath;
  }

  public List<String> getIncludes() {
    return myIncludes;
  }

  public List<String> getExcludes() {
    return myExcludes;
  }

  public String getScope() {
    return myScope;
  }

  public String getLang() {
    return myLang;
  }

  public String getTargetPath() {
    return myTargetPath;
  }

  public String getTargetVersion() {
    return myTargetVersion;
  }

  public boolean isFiltered() {
    return myFiltered;
  }

  public boolean isEnabled() {
    return myEnabled;
  }

  /**
   * @return true if this object was generated from maven 4-rc3+ source tag, false otherwise
   */
  public boolean isFromSourceTag() {
    return myIsSourceTag;
  }

  public MavenSource withNewDirectory(@NotNull String newDir) {
    return new MavenSource(
      myIsSourceTag,
      newDir,
      myIncludes,
      myExcludes,
      myScope,
      myLang,
      myTargetPath,
      myTargetVersion,
      myFiltered,
      myEnabled
    );
  }

  public static boolean isSource(MavenSource src) {
    return src.isEnabled() && (src.getScope() == null || src.getScope().equals(MAIN_SCOPE)) && JAVA_LANG.equals(src.getLang());
  }

  public static boolean isTestSource(MavenSource src) {
    return src.isEnabled() && src.getScope().equals(TEST_SCOPE) && JAVA_LANG.equals(src.getLang());
  }

  public static boolean isResource(MavenSource src) {
    return src.isEnabled() && (src.getScope() == null || src.getScope().equals(MAIN_SCOPE)) && RESOURCES_LANG.equals(src.getLang());
  }

  public static boolean isTestResource(MavenSource src) {
    return src.isEnabled() && src.getScope().equals(TEST_SCOPE) && RESOURCES_LANG.equals(src.getLang());
  }

  public static @NotNull MavenSource fromSrc(@NotNull String dir, boolean forTests) {
    return new MavenSource(false, dir, Collections.emptyList(), Collections.emptyList(), forTests ? TEST_SCOPE : MAIN_SCOPE, JAVA_LANG,
                           null, null,
                           false, true);
  }

  public static @NotNull MavenSource fromResource(MavenResource resource, boolean forTests) {
    return new MavenSource(false, resource.getDirectory(), resource.getIncludes(), resource.getExcludes(),
                           forTests ? TEST_SCOPE : MAIN_SCOPE,
                           RESOURCES_LANG, resource.getTargetPath(), null, resource.isFiltered(), true);
  }

  public static MavenSource fromSourceTag(@NotNull Path projectPomFile,
                                          String directory,
                                          List<String> includes,
                                          List<String> excludes,
                                          String scope,
                                          String lang,
                                          String targetPath,
                                          String targetVersion,
                                          boolean filtered,
                                          boolean enabled) {
    if (scope == null) {
      scope = MAIN_SCOPE;
    }
    if (lang == null) {
      lang = JAVA_LANG;
    }
    if (directory == null) {
      directory = "src/" + scope + "/" + lang;
    }
    Path absolute = getAbsolutePath(projectPomFile.getParent(), directory);
    return new MavenSource(true, absolute.toString(), includes, excludes, scope, lang, targetPath, targetVersion, filtered, enabled);
  }

  private static Path getAbsolutePath(@NotNull Path pomDirectory, @NotNull String directory) {
    Path p = parseToPath(directory);
    if (!p.isAbsolute()) {
      p = pomDirectory.resolve(p);
    }
    return p.toAbsolutePath().normalize();
  }

  private static Path parseToPath(@NotNull String directory) {
    String trimmed = directory.trim();

    // Handle file: URIs like file:///C:/path or file:/home/user/path
    if (trimmed.toLowerCase(Locale.ROOT).startsWith("file:")) {
      try {
        URI uri = new URI(trimmed);
        // Java 8 way
        return Paths.get(uri);
      }
      catch (URISyntaxException e) {
        throw new IllegalArgumentException("Invalid file URI: " + directory, e);
      }
      catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("Unsupported URI (expected file:): " + directory, e);
      }
    }

    try {
      return Paths.get(trimmed);
    }
    catch (InvalidPathException e) {
      throw new IllegalArgumentException("Invalid path: " + directory, e);
    }
  }


  @Override
  public final boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof MavenSource)) return false;

    MavenSource source = (MavenSource)o;
    return myFiltered == source.myFiltered &&
           myEnabled == source.myEnabled &&
           Objects.equals(myDirectoryAbsolutePath, source.myDirectoryAbsolutePath) &&
           Objects.equals(myIncludes, source.myIncludes) &&
           Objects.equals(myExcludes, source.myExcludes) &&
           Objects.equals(myScope, source.myScope) &&
           Objects.equals(myLang, source.myLang) &&
           Objects.equals(myTargetPath, source.myTargetPath) &&
           Objects.equals(myTargetVersion, source.myTargetVersion);
  }

  @Override
  public int hashCode() {
    int result = Objects.hashCode(myDirectoryAbsolutePath);
    result = 31 * result + Objects.hashCode(myIncludes);
    result = 31 * result + Objects.hashCode(myExcludes);
    result = 31 * result + Objects.hashCode(myScope);
    result = 31 * result + Objects.hashCode(myLang);
    result = 31 * result + Objects.hashCode(myTargetPath);
    result = 31 * result + Objects.hashCode(myTargetVersion);
    result = 31 * result + Boolean.hashCode(myFiltered);
    result = 31 * result + Boolean.hashCode(myEnabled);
    return result;
  }
}

