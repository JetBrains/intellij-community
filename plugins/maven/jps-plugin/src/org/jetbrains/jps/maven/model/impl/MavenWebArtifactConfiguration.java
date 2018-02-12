/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.jps.maven.model.impl;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.Transient;
import com.intellij.util.xmlb.annotations.XCollection;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Sergey Evdokimov
 */
public class MavenWebArtifactConfiguration {
  @Tag("module-name")
  public String moduleName;

  @XCollection(propertyElementName = "web-resources", elementName = "resource")
  public List<ResourceRootConfiguration> webResources = new ArrayList<>();

  @Tag("packaging-includes")
  public List<String> packagingIncludes = new ArrayList<>();

  @Tag("packaging-excludes")
  public List<String> packagingExcludes = new ArrayList<>();

  @Tag("war-root")
  public String warSourceDirectory = "src/main/webapp";

  @XCollection(propertyElementName = "war-source-includes", elementName = "include")
  public List<String> warSourceIncludes = new ArrayList<>();

  @XCollection(propertyElementName = "war-source-excludes", elementName = "exclude")
  public List<String> warSourceExcludes = new ArrayList<>();

  @Transient
  private volatile Map<File, ResourceRootConfiguration> myResourceRootsMap;

  @Nullable
  public ResourceRootConfiguration getRootConfiguration(@NotNull File root) {
    if (myResourceRootsMap == null) {
      Map<File, ResourceRootConfiguration> map = new THashMap<>(FileUtil.FILE_HASHING_STRATEGY);
      for (ResourceRootConfiguration resource : webResources) {
        map.put(new File(resource.directory), resource);
      }
      myResourceRootsMap = map;
    }
    return myResourceRootsMap.get(root);
  }
}
