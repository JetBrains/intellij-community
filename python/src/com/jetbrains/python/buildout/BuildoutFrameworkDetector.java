package com.jetbrains.python.buildout;

import com.intellij.facet.FacetType;
import com.intellij.framework.detection.FacetBasedFrameworkDetector;
import com.intellij.framework.detection.FileContentPattern;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.ElementPattern;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FileContent;
import com.jetbrains.python.buildout.config.BuildoutCfgFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;

/**
 * @author nik
 */
public class BuildoutFrameworkDetector extends FacetBasedFrameworkDetector<BuildoutFacet, BuildoutFacetConfiguration> {
  private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.buildout.BuildoutFrameworkDetector");

  public BuildoutFrameworkDetector() {
    super("buildout-python");
  }

  @Override
  public FacetType<BuildoutFacet, BuildoutFacetConfiguration> getFacetType() {
    return BuildoutFacetType.getInstance();
  }

  @NotNull
  @Override
  public FileType getFileType() {
    return BuildoutCfgFileType.INSTANCE;
  }

  @NotNull
  @Override
  public ElementPattern<FileContent> createSuitableFilePattern() {
    return FileContentPattern.fileContent();
  }

  @Nullable
  @Override
  protected BuildoutFacetConfiguration createConfiguration(Collection<VirtualFile> files) {
    VirtualFile source = ContainerUtil.getFirstItem(files);
    LOG.info("Detecting Buildout facet for " + source.getPath());
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
