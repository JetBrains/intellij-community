/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.buildout;

import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.application.Result;
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
public class BuildoutFacetConfigurator implements DirectoryProjectConfigurator {
  @Override
  public void configureProject(Project project, @NotNull VirtualFile baseDir, Ref<Module> moduleRef) {
    final Module[] modules = ModuleManager.getInstance(project).getModules();
    if (modules.length > 0) {
      final Module module = modules[0];
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
  }


  static void setupFacet(Module module, @NotNull BuildoutFacetConfiguration config) {
    //TODO: refactor, see other python facets
    FacetManager facetManager = FacetManager.getInstance(module);
    final ModifiableFacetModel model = facetManager.createModifiableModel();
    BuildoutFacetType facetType = BuildoutFacetType.getInstance();
    BuildoutFacet facet = facetManager.createFacet(facetType, facetType.getDefaultFacetName(), config, null);
    model.addFacet(facet);

    new WriteAction() {
      protected void run(@NotNull final Result result) throws Throwable {
        model.commit();
      }
    }.execute();
    facet.updatePaths();
    BuildoutFacet.attachLibrary(module);
  }
}
