package com.jetbrains.python.buildout;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetType;
import com.intellij.facet.FacetTypeId;
import com.intellij.facet.FacetTypeRegistry;
import com.intellij.facet.autodetecting.FacetDetector;
import com.intellij.facet.autodetecting.FacetDetectorRegistry;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.PythonModuleTypeBase;
import com.jetbrains.python.buildout.config.BuildoutCfgFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.Collection;

import static com.intellij.patterns.PlatformPatterns.virtualFile;

/**
 * Describes the buildout facet.
 * User: dcheryasov
 * Date: Jul 26, 2010 5:47:24 PM
 */
public class BuildoutFacetType extends FacetType<BuildoutFacet, BuildoutFacetConfiguration> {
  private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.buildout.BuildoutFacetType");

  private BuildoutFacetType() {
    super(ID, "buildout-python", "Buildout Support", null);
  }

  @Override
  public BuildoutFacetConfiguration createDefaultConfiguration() {
    return new BuildoutFacetConfiguration(null);
  }

  @Override
  public BuildoutFacet createFacet(@NotNull Module module,
                                   String name,
                                   @NotNull BuildoutFacetConfiguration configuration,
                                   @Nullable Facet underlyingFacet) {
    BuildoutFacet facet = new BuildoutFacet(this, module, name, configuration, underlyingFacet);
    return facet;
  }

  @Override
  public boolean isSuitableModuleType(ModuleType moduleType) {
    return moduleType instanceof PythonModuleTypeBase;
  }

  public static final FacetTypeId<BuildoutFacet> ID = new FacetTypeId<BuildoutFacet>("buildout-python");

  public static BuildoutFacetType getInstance() {
    return (BuildoutFacetType)FacetTypeRegistry.getInstance().findFacetType(ID);
  }

  public final static Icon BUILDOUT_ICON = IconLoader.getIcon("/com/jetbrains/python/icons/buildout/buildout.png");

  @Override
  public Icon getIcon() {
    return BUILDOUT_ICON;
  }

  @Override
  public void registerDetectors(FacetDetectorRegistry<BuildoutFacetConfiguration> registry) {
    registry.registerUniversalDetector(BuildoutCfgFileType.INSTANCE, virtualFile(), new BuildoutFacetDetector());
  }

  private static class BuildoutFacetDetector extends FacetDetector<VirtualFile,BuildoutFacetConfiguration> {
    @Override
    public BuildoutFacetConfiguration detectFacet(VirtualFile source, Collection<BuildoutFacetConfiguration> existentFacetConfigurations) {
      LOG.info("Detecting Buildout facet for " + source.getPath());
      if (existentFacetConfigurations.size() > 0) {
        LOG.info("Buildout facet already exists");
        return null;
      }
      final VirtualFile baseDir = source.getParent();
      final VirtualFile runner = BuildoutFacet.getRunner(baseDir);
      if (runner != null) {
        final File script = BuildoutFacet.findScript(null, "buildout", baseDir);
        if (script != null) {
          BuildoutFacetConfiguration configuration = new BuildoutFacetConfiguration(script.getName());
          configuration.setScriptName(script.getPath());
          final VirtualFile scriptVFile = LocalFileSystem.getInstance().findFileByIoFile(script);
          if (scriptVFile != null) {
            configuration.setPaths(BuildoutFacet.extractBuildoutPaths(scriptVFile));
          }
          else {
            LOG.info("Could not find virtual file for buildout script " + script);
          }
          return configuration;
        }
        else {
          LOG.info("No buildout script found");
        }
      }
      else {
        LOG.info("No runner script found");
      }
      return null;
    }
  }
}
