package com.jetbrains.python.buildout;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetType;
import com.intellij.facet.FacetTypeId;
import com.intellij.facet.FacetTypeRegistry;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.jetbrains.python.PythonModuleTypeBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Describes the buildout facet.
 * User: dcheryasov
 * Date: Jul 26, 2010 5:47:24 PM
 */
public class BuildoutFacetType extends FacetType<BuildoutFacet, BuildoutFacetConfiguration> {
  private BuildoutFacetType() {
    super(ID, "buildout-python", "Buildout Support", null);
  }

  @Override
  public BuildoutFacetConfiguration createDefaultConfiguration() {
    Project project = PlatformDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext());
    return new BuildoutFacetConfiguration(null);
    // TODO: can there be no project?
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

  public final static Icon ourIcon = IconLoader.getIcon("/com/jetbrains/python/icons/buildout/buildout.png");

  @Override
  public Icon getIcon() {
    return ourIcon;
  }
}
