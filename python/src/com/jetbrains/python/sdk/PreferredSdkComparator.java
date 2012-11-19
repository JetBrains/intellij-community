package com.jetbrains.python.sdk;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Comparing;
import com.jetbrains.python.sdk.flavors.CPythonSdkFlavor;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;

import java.util.Comparator;

/**
* @author yole
*/
public class PreferredSdkComparator implements Comparator<Sdk> {
  @Override
  public int compare(Sdk o1, Sdk o2) {
    final PythonSdkFlavor flavor1 = PythonSdkFlavor.getFlavor(o1);
    final PythonSdkFlavor flavor2 = PythonSdkFlavor.getFlavor(o2);
    int flavor1weight = flavor1 instanceof CPythonSdkFlavor ? 1 : 0;
    int flavor2weight = flavor2 instanceof CPythonSdkFlavor ? 1 : 0;
    if (flavor1weight != flavor2weight) {
      return flavor2weight - flavor1weight;
    }
    return -Comparing.compare(o1.getVersionString(), o2.getVersionString());
  }
}
