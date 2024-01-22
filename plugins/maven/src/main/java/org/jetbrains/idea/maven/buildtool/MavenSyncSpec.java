// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.buildtool;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.NonExtendable
@ApiStatus.Experimental
public interface MavenSyncSpec {
  MavenSyncSpec INCREMENTAL = new MavenSyncSpecImpl(false, false, "");
  MavenSyncSpec INCREMENTAL_EXPLICIT = new MavenSyncSpecImpl(false, true, "");

  MavenSyncSpec FULL = new MavenSyncSpecImpl(true, false, "");
  MavenSyncSpec FULL_EXPLICIT = new MavenSyncSpecImpl(true, true, "");

  boolean isForceReading();

  boolean isExplicitImport();
}

class MavenSyncSpecImpl implements MavenSyncSpec {
  private final boolean myForceReading;
  private final boolean myExplicitImport;
  private final String description;

  MavenSyncSpecImpl(boolean forceReading, boolean explicitImport, String description) {
    myForceReading = forceReading;
    myExplicitImport = explicitImport;
    this.description = description;
  }

  @Override
  public boolean isForceReading() {
    return myForceReading;
  }

  @Override
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
