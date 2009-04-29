package com.jetbrains.python.facet;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetType;
import com.intellij.facet.FacetTypeRegistry;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.IconLoader;
import com.jetbrains.python.module.PythonModuleType;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * @author yole
 */
public class PythonFacetType extends FacetType<PythonFacet, PythonFacetConfiguration> {
  private static final Icon ICON = IconLoader.findIcon("/com/jetbrains/python/python.png");

  @NonNls
  private static final String ID = "Python";

  public static PythonFacetType getInstance() {
    final PythonFacetType facetType = (PythonFacetType)FacetTypeRegistry.getInstance().findFacetType(PythonFacet.ID);
    assert facetType != null;
    return facetType;
  }

  public PythonFacetType() {
    super(PythonFacet.ID, ID, "Python");
  }

  public PythonFacetConfiguration createDefaultConfiguration() {
    PythonFacetConfiguration result = new PythonFacetConfiguration();
    List<Sdk> sdks = ProjectJdkTable.getInstance().getSdksOfType(PythonSdkType.getInstance());
    if (sdks.size() > 0) {
      result.setSdk(sdks.get(0));
    }
    return result;
  }

  public PythonFacet createFacet(@NotNull Module module, String name, @NotNull PythonFacetConfiguration configuration, @Nullable Facet underlyingFacet) {
    return new PythonFacet(this, module, name, configuration, underlyingFacet);
  }

  public boolean isSuitableModuleType(ModuleType moduleType) {
    return !(moduleType instanceof PythonModuleType);
  }

  @Override
  public Icon getIcon() {
    return ICON;
  }
}
