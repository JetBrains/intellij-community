package com.jetbrains.python.facet;

import org.jetbrains.annotations.NonNls;

/**
 * @author yole
 */
public interface LibraryContributingFacet {
  @NonNls String PYTHON_FACET_LIBRARY_NAME_SUFFIX = " interpreter library";

  void updateLibrary();
  void removeLibrary();
}
