// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.buildout;

import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.DirectoryProjectConfigurator;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * Detects and configures a buildout facet.
 * User: dcheryasov
 */
public final class BuildoutFacetConfigurator implements DirectoryProjectConfigurator {
  @Override
  public void configureProject(@NotNull Project project, @NotNull VirtualFile baseDir, @NotNull Ref<Module> moduleRef, boolean isProjectCreatedWithWizard) {
    final Module[] modules = ModuleManager.getInstance(project).getModules();
    if (modules.length <= 0) {
      return;
    }

    Module module = modules[0];
    if (BuildoutFacet.getInstance(module) == null) {
      baseDir.refresh(false, false);
      final VirtualFile runner = BuildoutFacet.getRunner(baseDir);
      if (runner != null) {
        // TODO parse buildout.cfg and find out the part to use for the default script
        final File script = BuildoutFacet.findScript(null, "buildout", project.getBaseDir());
        if (script != null) {
          final ProjectFacetManager facetManager = ProjectFacetManager.getInstance(module.getProject());
          final BuildoutFacetConfiguration config = facetManager.createDefaultConfiguration(BuildoutFacetType.getInstance());
          config.setScriptName(script.getPath());
          setupFacet(module, config);
        }
      }
    }
  }

  static void setupFacet(Module module, @NotNull BuildoutFacetConfiguration config) {
    //TODO: refactor, see other python facets
    FacetManager facetManager = FacetManager.getInstance(module);
    final ModifiableFacetModel model = facetManager.createModifiableModel();
    BuildoutFacetType facetType = BuildoutFacetType.getInstance();
    BuildoutFacet facet = facetManager.createFacet(facetType, facetType.getDefaultFacetName(), config, null);
    model.addFacet(facet);

    WriteAction.run(() -> model.commit());
    facet.updatePaths();
    BuildoutFacet.attachLibrary(module);
  }
}
