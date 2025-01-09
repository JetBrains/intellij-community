// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.maven.model.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.FileCollectionFactory;
import com.intellij.util.xmlb.XmlSerializer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.incremental.resources.ResourcesBuilder;
import org.jetbrains.jps.incremental.resources.StandardResourceBuilderEnabler;
import org.jetbrains.jps.maven.model.JpsMavenExtensionService;
import org.jetbrains.jps.maven.model.JpsMavenModuleExtension;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.JpsSimpleElement;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import org.jetbrains.jps.model.java.impl.JpsJavaAwareProject;
import org.jetbrains.jps.model.module.JpsDependencyElement;
import org.jetbrains.jps.model.module.JpsModule;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class JpsMavenExtensionServiceImpl extends JpsMavenExtensionService {
  private static final Logger LOG = Logger.getInstance(JpsMavenExtensionServiceImpl.class);
  private static final JpsElementChildRole<JpsSimpleElement<Boolean>> PRODUCTION_ON_TEST_ROLE = JpsElementChildRoleBase.create("maven production on test");
  private final Map<Path, MavenProjectConfiguration> myLoadedConfigs = FileCollectionFactory.createCanonicalPathMap();
  private final Map<Path, Boolean> myConfigFileExists = ConcurrentFactoryMap.createMap(key -> Files.exists(key));

  public JpsMavenExtensionServiceImpl() {
    ResourcesBuilder.registerEnabler(new StandardResourceBuilderEnabler() {
      @Override
      public boolean isResourceProcessingEnabled(JpsModule module) {
        // enable standard resource processing only if this is not a maven module
        // for maven modules use maven-aware resource builder
        return getExtension(module) == null;
      }
    });
  }

  @Override
  public @Nullable JpsMavenModuleExtension getExtension(@NotNull JpsModule module) {
    return module.getContainer().getChild(JpsMavenModuleExtensionImpl.ROLE);
  }

  @Override
  public @NotNull JpsMavenModuleExtension getOrCreateExtension(@NotNull JpsModule module) {
    JpsMavenModuleExtension extension = module.getContainer().getChild(JpsMavenModuleExtensionImpl.ROLE);
    if (extension == null) {
      extension = new JpsMavenModuleExtensionImpl();
      module.getContainer().setChild(JpsMavenModuleExtensionImpl.ROLE, extension);
    }
    return extension;
  }

  @Override
  public void setProductionOnTestDependency(@NotNull JpsDependencyElement dependency, boolean value) {
    if (value) {
      dependency.getContainer().setChild(PRODUCTION_ON_TEST_ROLE, JpsElementFactory.getInstance().createSimpleElement(true));
    }
    else {
      dependency.getContainer().removeChild(PRODUCTION_ON_TEST_ROLE);
    }
  }

  @Override
  public boolean isProductionOnTestDependency(@NotNull JpsDependencyElement dependency) {
    JpsProject project = dependency.getContainingModule().getProject();
    if (project instanceof JpsJavaAwareProject) {
      return ((JpsJavaAwareProject)project).isProductionOnTestDependency(dependency);
    }
    JpsSimpleElement<Boolean> child = dependency.getContainer().getChild(PRODUCTION_ON_TEST_ROLE);
    return child != null && child.getData();
  }

  @Override
  public boolean hasMavenProjectConfiguration(@NotNull BuildDataPaths paths) {
    return myConfigFileExists.get(paths.getDataStorageDir().resolve(MavenProjectConfiguration.CONFIGURATION_FILE_RELATIVE_PATH));
  }

  @Override
  public MavenProjectConfiguration getMavenProjectConfiguration(@NotNull BuildDataPaths paths) {
    return hasMavenProjectConfiguration(paths) ? getMavenProjectConfiguration(paths.getDataStorageDir()) : null;
  }

  public @NotNull MavenProjectConfiguration getMavenProjectConfiguration(@NotNull Path dataStorageRoot) {
    Path configFile = dataStorageRoot.resolve(MavenProjectConfiguration.CONFIGURATION_FILE_RELATIVE_PATH);
    MavenProjectConfiguration config;
    synchronized (myLoadedConfigs) {
      config = myLoadedConfigs.get(configFile);
      if (config == null) {
        config = new MavenProjectConfiguration();
        try {
          XmlSerializer.deserializeInto(config, JDOMUtil.load(configFile));
        }
        catch (Exception e) {
          LOG.info(e);
        }
        myLoadedConfigs.put(configFile, config);
      }
    }
    return config;
  }
}
