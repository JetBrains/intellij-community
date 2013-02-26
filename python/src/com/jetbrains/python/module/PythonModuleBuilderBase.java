package com.jetbrains.python.module;

import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.jetbrains.python.PythonModuleTypeBase;
import com.jetbrains.python.sdk.PythonSdkType;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class PythonModuleBuilderBase extends ModuleBuilder {
  private final List<Runnable> mySdkChangedListeners = new ArrayList<Runnable>();
  private Sdk mySdk;

  public void setupRootModel(final ModifiableRootModel rootModel) throws ConfigurationException {
    if (mySdk != null) {
      rootModel.setSdk(mySdk);
    }
    else {
      rootModel.inheritSdk();
    }

    doAddContentEntry(rootModel);
  }

  @Override
  public ModuleType getModuleType() {
    return PythonModuleTypeBase.getInstance();
  }

  public Sdk getSdk() {
    return mySdk;
  }

  public void setSdk(final Sdk sdk) {
    if (mySdk != sdk) {
      mySdk = sdk;
      for (Runnable runnable : mySdkChangedListeners) {
        runnable.run();
      }
    }
  }

  public void addSdkChangedListener(Runnable runnable) {
    mySdkChangedListeners.add(runnable);
  }

  @Override
  public boolean isSuitableSdkType(SdkTypeId sdk) {
    return sdk instanceof PythonSdkType;
  }
}
