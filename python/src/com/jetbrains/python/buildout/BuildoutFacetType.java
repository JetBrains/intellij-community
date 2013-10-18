package com.jetbrains.python.buildout;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetType;
import com.intellij.facet.FacetTypeId;
import com.intellij.facet.FacetTypeRegistry;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.jetbrains.python.PythonModuleTypeBase;
import icons.PythonIcons;
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

  @Override
  public Icon getIcon() {
    return PythonIcons.Python.Buildout.Buildout;
  }
}
