// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.buildtool;

public class MavenImportSpec {

  public static final MavenImportSpec EXPLICIT_IMPORT = new MavenImportSpec(true, true);
  public static final MavenImportSpec IMPLICIT_IMPORT = new MavenImportSpec(true, false);

  private final boolean myForceReading;
  private final boolean myExplicitImport;

  public MavenImportSpec(boolean forceReading, boolean explicitImport) {
    myForceReading = forceReading;
    myExplicitImport = explicitImport;
  }

  public boolean isForceReading() {
    return myForceReading;
  }

  public boolean isExplicitImport() {
    return myExplicitImport;
  }

  @Override
  public String toString() {
    return "MavenImportSpec{" +
           "forceReading=" + myForceReading +
           ", explicit=" + myExplicitImport +
           '}';
  }
}
