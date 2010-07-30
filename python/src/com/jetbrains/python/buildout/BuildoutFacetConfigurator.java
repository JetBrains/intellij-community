package com.jetbrains.python.buildout;

import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.DirectoryProjectConfigurator;
import org.jetbrains.annotations.NotNull;

/**
 * Detects and configures a buildout facet.
 * User: dcheryasov
 * Date: Jul 26, 2010 6:10:39 PM
 */
public class BuildoutFacetConfigurator implements DirectoryProjectConfigurator {
  @Override
  public void configureProject(Project project, @NotNull VirtualFile baseDir) {
    final Module[] modules = ModuleManager.getInstance(project).getModules();
    if (modules.length > 0) {
      final Module module = modules[0];
      if (BuildoutFacet.getInstance(module) == null) {
        if (BuildoutFacet.getBinDir(baseDir) != null) setupFacet(module);
      }
    }
  }


  static void setupFacet(Module module) {
    //TODO: refactor, see other python facets
    FacetManager facetManager = FacetManager.getInstance(module);
    final ModifiableFacetModel model = facetManager.createModifiableModel();
    BuildoutFacetType facetType = BuildoutFacetType.getInstance();
    BuildoutFacetConfiguration config = ProjectFacetManager.getInstance(module.getProject()).createDefaultConfiguration(facetType);
    BuildoutFacet facet = facetManager.createFacet(facetType, facetType.getDefaultFacetName(), config, null);
    model.addFacet(facet);

    new WriteAction() {
      protected void run(final Result result) throws Throwable {
        model.commit();
      }
    }.execute();
  }
}
