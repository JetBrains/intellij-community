// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.facet;

import org.jetbrains.annotations.ApiStatus;
import java.util.List;


/**
 * @deprecated No replacement exists.
 */
@Deprecated
@ApiStatus.Internal
public interface PythonPathContributingFacet {
  List<String> getAdditionalPythonPath();

  boolean acceptRootAsTopLevelPackage();
}
