package org.jetbrains.jps.maven.model.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.xmlb.XmlSerializer;
import gnu.trove.THashMap;
import org.jdom.Document;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.incremental.resources.ResourcesBuilder;
import org.jetbrains.jps.incremental.resources.StandardResourceBuilderEnabler;
import org.jetbrains.jps.maven.model.JpsMavenExtensionService;
import org.jetbrains.jps.maven.model.JpsMavenModuleExtension;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.JpsSimpleElement;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import org.jetbrains.jps.model.module.JpsDependencyElement;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.util.Map;

/**
 * @author nik
 */
public class JpsMavenExtensionServiceImpl extends JpsMavenExtensionService {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.maven.model.impl.JpsMavenExtensionServiceImpl");
  private static final JpsElementChildRole<JpsSimpleElement<Boolean>> PRODUCTION_ON_TEST_ROLE = JpsElementChildRoleBase.create("production on test");
  private final Map<File, MavenProjectConfiguration> myLoadedConfigs =
    new THashMap<File, MavenProjectConfiguration>(FileUtil.FILE_HASHING_STRATEGY);
  private final FactoryMap<File, Boolean> myConfigFileExists = new ConcurrentFactoryMap<File, Boolean>() {
    @Nullable
    @Override
    protected Boolean create(File key) {
      return key.exists();
    }
  };

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

  @Nullable
  @Override
  public JpsMavenModuleExtension getExtension(@NotNull JpsModule module) {
    return module.getContainer().getChild(JpsMavenModuleExtensionImpl.ROLE);
  }

  @NotNull
  @Override
  public JpsMavenModuleExtension getOrCreateExtension(@NotNull JpsModule module) {
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
    JpsSimpleElement<Boolean> child = dependency.getContainer().getChild(PRODUCTION_ON_TEST_ROLE);
    return child != null && child.getData();
  }

  @Override
  public boolean hasMavenProjectConfiguration(@NotNull BuildDataPaths paths) {
    return myConfigFileExists.get(new File(paths.getDataStorageRoot(), MavenProjectConfiguration.CONFIGURATION_FILE_RELATIVE_PATH));
  }

  @Override
  public MavenProjectConfiguration getMavenProjectConfiguration(BuildDataPaths paths) {
    if (!hasMavenProjectConfiguration(paths)) {
      return null;
    }
    final File dataStorageRoot = paths.getDataStorageRoot();
    return getMavenProjectConfiguration(dataStorageRoot);
  }

  @NotNull
  public MavenProjectConfiguration getMavenProjectConfiguration(@NotNull File dataStorageRoot) {
    final File configFile = new File(dataStorageRoot, MavenProjectConfiguration.CONFIGURATION_FILE_RELATIVE_PATH);
    MavenProjectConfiguration config;
    synchronized (myLoadedConfigs) {
      config = myLoadedConfigs.get(configFile);
      if (config == null) {
        config = new MavenProjectConfiguration();
        try {
          final Document document = JDOMUtil.loadDocument(configFile);
          XmlSerializer.deserializeInto(config, document.getRootElement());
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
