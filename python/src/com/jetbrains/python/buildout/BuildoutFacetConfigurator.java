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

import java.io.File;

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
      protected void run(final Result result) throws Throwable {
        model.commit();
      }
    }.execute();
    facet.updatePaths();
    BuildoutConfigurable.attachLibrary(module);
  }
}
