package com.jetbrains.python.module;

import com.intellij.ide.util.importProject.ModuleDescriptor;
import com.intellij.ide.util.importProject.ProjectDescriptor;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.ProjectWizardStepFactory;
import com.intellij.ide.util.projectWizard.importSources.DetectedProjectRoot;
import com.intellij.ide.util.projectWizard.importSources.ProjectFromSourcesBuilder;
import com.intellij.ide.util.projectWizard.importSources.ProjectStructureDetector;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class PyProjectStructureDetector extends ProjectStructureDetector {
  private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.module.PyProjectStructureDetector"); 
  
  
  @NotNull
  @Override
  public DirectoryProcessingResult detectRoots(@NotNull File dir,
                                               @NotNull File[] children,
                                               @NotNull File base,
                                               @NotNull List<DetectedProjectRoot> result) {
    LOG.info("Detecting roots under "  + dir);
    for (File child : children) {
      if (FileUtil.getExtension(child.getName()).equals("py")) {
        LOG.info("Found Python file " + child.getPath());
        result.add(new DetectedProjectRoot(dir) {
          @NotNull
          @Override
          public String getRootTypeName() {
            return "Python";
          }
        });
        return DirectoryProcessingResult.SKIP_CHILDREN;
      }
    }
    return DirectoryProcessingResult.PROCESS_CHILDREN;
  }

  @Override
  public void setupProjectStructure(@NotNull Collection<DetectedProjectRoot> roots,
                                    @NotNull ProjectDescriptor projectDescriptor,
                                    @NotNull ProjectFromSourcesBuilder builder) {
    if (!roots.isEmpty() && !builder.hasRootsFromOtherDetectors(this)) {
      List<ModuleDescriptor> modules = projectDescriptor.getModules();
      if (modules.isEmpty()) {
        modules = new ArrayList<ModuleDescriptor>();
        for (DetectedProjectRoot root : roots) {
          modules.add(new ModuleDescriptor(root.getDirectory(), PythonModuleType.getInstance(), root));
        }
        projectDescriptor.setModules(modules);
      }
    }
  }

  @Override
  public List<ModuleWizardStep> createWizardSteps(ProjectFromSourcesBuilder builder, ProjectDescriptor projectDescriptor, Icon stepIcon) {
    return Collections.singletonList(ProjectWizardStepFactory.getInstance().createProjectJdkStep(builder.getContext()));
  }
}
