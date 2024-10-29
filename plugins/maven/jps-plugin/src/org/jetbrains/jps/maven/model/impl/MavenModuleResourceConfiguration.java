// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.maven.model.impl;

import com.dynatrace.hash4j.hashing.HashFunnel;
import com.dynatrace.hash4j.hashing.HashSink;
import com.dynatrace.hash4j.hashing.Hashing;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.xmlb.annotations.MapAnnotation;
import com.intellij.util.xmlb.annotations.OptionTag;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Eugene Zhuravlev
 */
@ApiStatus.Internal
public final class MavenModuleResourceConfiguration {
  @Tag("id") public @NotNull MavenIdBean id;

  @Tag("parentId") public @Nullable MavenIdBean parentId;

  @Tag("directory") public @NotNull String directory;

  @Tag("manifest") public @Nullable String manifest;

  @Tag("classpath") public @Nullable String classpath;

  @Tag("delimiters-pattern") public @NotNull String delimitersPattern;

  @Tag("model-map")
  @MapAnnotation(surroundWithTag = false, surroundKeyWithTag = false, surroundValueWithTag = false)
  public Map<String, String> modelMap = new HashMap<>();

  @Tag("properties")
  @MapAnnotation(surroundWithTag = false, surroundKeyWithTag = false, surroundValueWithTag = false)
  public Map<String, String> properties = new HashMap<>();

  @XCollection(propertyElementName = "filtering-excluded-extensions", elementName = "extension")
  public Set<String> filteringExclusions = CollectionFactory.createFilePathSet();

  @OptionTag
  public String escapeString = null;

  @OptionTag
  public boolean escapeWindowsPaths = true;

  @OptionTag
  public boolean overwrite;

  @OptionTag
  public String outputDirectory = null;

  @OptionTag
  public String testOutputDirectory = null;

  @XCollection(propertyElementName = "resources", elementName = "resource")
  public List<ResourceRootConfiguration> resources = new ArrayList<>();

  @XCollection(propertyElementName = "test-resources", elementName = "resource")
  public List<ResourceRootConfiguration> testResources = new ArrayList<>();

  public Set<String> getFilteringExcludedExtensions() {
    if (filteringExclusions.isEmpty()) {
      return MavenProjectConfiguration.DEFAULT_FILTERING_EXCLUDED_EXTENSIONS;
    }
    final Set<String> result = CollectionFactory.createFilePathSet();
    result.addAll(MavenProjectConfiguration.DEFAULT_FILTERING_EXCLUDED_EXTENSIONS);
    result.addAll(filteringExclusions);
    return Collections.unmodifiableSet(result);
  }

  public void computeConfigurationHash(boolean forTestResources, @NotNull HashSink hash) {
    computeModuleConfigurationHash(hash);

    List<ResourceRootConfiguration> _resources = forTestResources? testResources : resources;
    for (ResourceRootConfiguration resource : _resources) {
      resource.computeConfigurationHash(hash);
    }
    hash.putInt(_resources.size());
  }

  public void computeModuleConfigurationHash(@NotNull HashSink hash) {
    hash.putInt(parentId == null ? 0 : parentId.hashCode());
    hash.putString(directory);
    hashNullableString(manifest, hash);
    hashNullableString(classpath, hash);
    hash.putString(delimitersPattern);

    hash.putUnorderedIterable(filteringExclusions, HashFunnel.forString(), Hashing.komihash5_0());

    hashNullableString(escapeString, hash);
    hashNullableString(outputDirectory, hash);
    hashNullableString(testOutputDirectory, hash);

    HashFunnel<Map.Entry<String, String>> entryHashFunnel = HashFunnel.forEntry(HashFunnel.forString(), HashFunnel.forString());
    hash.putUnorderedIterable(modelMap.entrySet(), entryHashFunnel, Hashing.komihash5_0());
    hash.putUnorderedIterable(properties.entrySet(), entryHashFunnel, Hashing.komihash5_0());

    hash.putBoolean(escapeWindowsPaths);
    hash.putBoolean(overwrite);
  }

  private static void hashNullableString(@Nullable String s, @NotNull HashSink hash) {
    if (s == null) {
      hash.putInt(-1);
    }
    else {
      hash.putString(s);
    }
  }

  @Override
  public String toString() {
    return "MavenModuleResourceConfiguration{" +
           "id=" + id +
           ", parentId=" + parentId +
           ", directory='" + directory + '\'' +
           ", manifest='" + manifest + '\'' +
           ", classpath='" + classpath + '\'' +
           ", delimitersPattern='" + delimitersPattern + '\'' +
           ", modelMap=" + modelMap +
           ", properties=" + properties +
           ", filteringExclusions=" + filteringExclusions +
           ", escapeString='" + escapeString + '\'' +
           ", escapeWindowsPaths=" + escapeWindowsPaths +
           ", overwrite=" + overwrite +
           ", outputDirectory='" + outputDirectory + '\'' +
           ", testOutputDirectory='" + testOutputDirectory + '\'' +
           ", resources=" + resources +
           ", testResources=" + testResources +
           '}';
  }
}



