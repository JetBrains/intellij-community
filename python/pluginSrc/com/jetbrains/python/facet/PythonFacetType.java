package com.jetbrains.python.facet;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetType;
import com.intellij.facet.FacetTypeRegistry;
import com.intellij.facet.autodetecting.FacetDetector;
import com.intellij.facet.autodetecting.FacetDetectorRegistry;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.psi.PsiFile;
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

  @Override
  public void registerDetectors(FacetDetectorRegistry<PythonFacetConfiguration> detectorRegistry) {
    final VirtualFileFilter pythonFacetFilter = new VirtualFileFilter() {
      public boolean accept(VirtualFile virtualFile) {
        return virtualFile.getFileType() instanceof PythonFileType;
      }
    };

    final Condition<PsiFile> condition = new Condition<PsiFile>() {
      public boolean value(PsiFile psiFile) {
        final VirtualFile vFile = psiFile.getVirtualFile();
        if (vFile == null) {
          return false;
        }
        final Module module = ModuleUtil.findModuleForFile(vFile, psiFile.getProject());
        return !(module.getModuleType() instanceof PythonModuleType) && vFile.getFileType() instanceof PythonFileType;
      }
    };
    detectorRegistry
        .registerOnTheFlyDetector(PythonFileType.INSTANCE, pythonFacetFilter, condition, new FacetDetector<PsiFile, PythonFacetConfiguration>("python-detector-psi") {
          public PythonFacetConfiguration detectFacet(PsiFile source, Collection<PythonFacetConfiguration> existentFacetConfigurations) {
            return existentFacetConfigurations.isEmpty() ? createDefaultConfiguration() : existentFacetConfigurations.iterator().next();
          }
        });

    detectorRegistry
        .registerDetectorForWizard(PythonFileType.INSTANCE, pythonFacetFilter, new FacetDetector<VirtualFile, PythonFacetConfiguration>("python-detector") {
          public PythonFacetConfiguration detectFacet(VirtualFile source, Collection<PythonFacetConfiguration> existentFacetConfigurations) {
            return existentFacetConfigurations.isEmpty() ? createDefaultConfiguration() : existentFacetConfigurations.iterator().next();
          }
        });
  }
}
