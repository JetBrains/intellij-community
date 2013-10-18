package com.jetbrains.python.facet;

import java.util.List;

/**
 * @author yole
 */
public interface PythonPathContributingFacet {
  List<String> getAdditionalPythonPath();
  boolean acceptRootAsTopLevelPackage();
}
