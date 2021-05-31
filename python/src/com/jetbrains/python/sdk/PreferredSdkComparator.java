// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Comparing;
import com.jetbrains.python.sdk.flavors.CPythonSdkFlavor;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;

import java.util.Comparator;

public class PreferredSdkComparator implements Comparator<Sdk> {
  public static final PreferredSdkComparator INSTANCE = new PreferredSdkComparator();

  @Override
  public int compare(Sdk o1, Sdk o2) {
    for (PySdkComparator comparator : PySdkComparator.EP_NAME.getExtensionList()) {
      int result = comparator.compare(o1, o2);
      if(result != 0) {
        return result;
      }
    }

    final PythonSdkFlavor flavor1 = PythonSdkFlavor.getFlavor(o1);
    final PythonSdkFlavor flavor2 = PythonSdkFlavor.getFlavor(o2);
    int remote1Weight = PythonSdkUtil.isRemote(o1) ? 0 : 1;
    int remote2Weight = PythonSdkUtil.isRemote(o2) ? 0 : 1;
    if (remote1Weight != remote2Weight) {
      return remote2Weight - remote1Weight;
    }
    int detectedWeight1 = o1 instanceof PyDetectedSdk ? 0 : 1;
    int detectedWeight2 = o2 instanceof PyDetectedSdk ? 0 : 1;
    if (detectedWeight1 != detectedWeight2) {
      return detectedWeight2 - detectedWeight1;
    }

    int venv1weight = PythonSdkUtil.isVirtualEnv(o1) || PythonSdkUtil.isCondaVirtualEnv(o1) ? 0 : 1;
    int venv2weight = PythonSdkUtil.isVirtualEnv(o2) || PythonSdkUtil.isCondaVirtualEnv(o2) ? 0 : 1;
    if (venv1weight != venv2weight) {
      return venv2weight - venv1weight;
    }

    int flavor1weight = flavor1 instanceof CPythonSdkFlavor ? 1 : 0;
    int flavor2weight = flavor2 instanceof CPythonSdkFlavor ? 1 : 0;
    if (flavor1weight != flavor2weight) {
      return flavor2weight - flavor1weight;
    }

    return -Comparing.compare(o1.getVersionString(), o2.getVersionString());
  }
}
