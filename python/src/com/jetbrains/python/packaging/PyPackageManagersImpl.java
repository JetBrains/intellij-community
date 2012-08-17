package com.jetbrains.python.packaging;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author yole
 */
public class PyPackageManagersImpl extends PyPackageManagers {
  private final Map<String, PyPackageManagerImpl> myInstances = new HashMap<String, PyPackageManagerImpl>();

  @Override
  public synchronized PyPackageManager forSdk(Sdk sdk) {
    final String name = sdk.getName();
    PyPackageManagerImpl manager = myInstances.get(name);
    if (manager == null) {
      manager = new PyPackageManagerImpl(sdk);
      myInstances.put(name, manager);
    }
    return manager;
  }

  @Nullable
  @Override
  public List<PyRequirement> getRequirements(Module module) {
    return PyPackageManagerImpl.getRequirements(module);
  }


  @Nullable
  @Override
  public List<PyRequirement> getRequirementsFromTxt(Module module) {
    return PyPackageManagerImpl.getRequirementsFromTxt(module);
  }
}
