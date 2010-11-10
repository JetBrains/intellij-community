package com.jetbrains.python.facet;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetType;
import com.intellij.facet.autodetecting.FacetDetector;
import com.intellij.facet.autodetecting.FacetDetectorRegistry;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.module.PythonModuleType;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.List;

/**
 * @author yole
 */
public class PythonFacetType extends FacetType<PythonFacet, PythonFacetConfiguration> {
  public static final Icon ICON = IconLoader.getIcon("/com/jetbrains/python/icons/python.png");

  @NonNls
  private static final String ID = "Python";

  public static PythonFacetType getInstance() {
    return findInstance(PythonFacetType.class);
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

  @Override
  public void registerDetectors(FacetDetectorRegistry<PythonFacetConfiguration> detectorRegistry) {
    detectorRegistry.registerUniversalDetector(PythonFileType.INSTANCE, VirtualFileFilter.ALL, new FacetDetector<VirtualFile, PythonFacetConfiguration>("python-detector") {
      public PythonFacetConfiguration detectFacet(VirtualFile source,
                                                  Collection<PythonFacetConfiguration> existentFacetConfigurations) {
        return existentFacetConfigurations.isEmpty()
               ? createDefaultConfiguration()
               : existentFacetConfigurations.iterator().next();
      }
    });
  }
}
