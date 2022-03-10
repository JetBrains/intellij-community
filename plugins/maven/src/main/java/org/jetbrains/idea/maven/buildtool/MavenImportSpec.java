// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.buildtool;

public class MavenImportSpec {

  public static final MavenImportSpec EXPLICIT_IMPORT = new MavenImportSpec(true, true, true);
  public static final MavenImportSpec IMPLICIT_IMPORT = new MavenImportSpec(true, true, false);

  private final boolean myForceReading;
  private final boolean myForceResolve;
  private final boolean myExplicitImport;

  public MavenImportSpec(boolean forceReading, boolean forceImportAndResolve, boolean explicitImport) {
    myForceReading = forceReading;
    myForceResolve = forceImportAndResolve;
    myExplicitImport = explicitImport;
  }

  public boolean isForceReading() {
    return myForceReading;
  }

  public boolean isForceResolve() {
    return myForceResolve;
  }

  public boolean isExplicitImport() {
    return myExplicitImport;
  }
}
