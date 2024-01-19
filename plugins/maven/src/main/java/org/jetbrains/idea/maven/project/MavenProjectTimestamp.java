// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project;

import org.jetbrains.annotations.ApiStatus;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

@ApiStatus.Internal
final class MavenProjectTimestamp {
  private final long myPomTimestamp;
  private final long myParentLastReadStamp;
  private final long myProfilesTimestamp;
  private final long myUserSettingsTimestamp;
  private final long myGlobalSettingsTimestamp;
  private final long myExplicitProfilesHashCode;
  private final long myJvmConfigTimestamp;
  private final long myMavenConfigTimestamp;

  static MavenProjectTimestamp NULL = new MavenProjectTimestamp(0, 0, 0, 0, 0, 0, 0, 0);

  MavenProjectTimestamp(long pomTimestamp,
                        long parentLastReadStamp,
                        long profilesTimestamp,
                        long userSettingsTimestamp,
                        long globalSettingsTimestamp,
                        long explicitProfilesHashCode,
                        long jvmConfigTimestamp,
                        long mavenConfigTimestamp) {
    myPomTimestamp = pomTimestamp;
    myParentLastReadStamp = parentLastReadStamp;
    myProfilesTimestamp = profilesTimestamp;
    myUserSettingsTimestamp = userSettingsTimestamp;
    myGlobalSettingsTimestamp = globalSettingsTimestamp;
    myExplicitProfilesHashCode = explicitProfilesHashCode;
    myJvmConfigTimestamp = jvmConfigTimestamp;
    myMavenConfigTimestamp = mavenConfigTimestamp;
  }

  public static MavenProjectTimestamp read(DataInputStream in) throws IOException {
    return new MavenProjectTimestamp(in.readLong(),
                                     in.readLong(),
                                     in.readLong(),
                                     in.readLong(),
                                     in.readLong(),
                                     in.readLong(),
                                     in.readLong(),
                                     in.readLong());
  }

  public void write(DataOutputStream out) throws IOException {
    out.writeLong(myPomTimestamp);
    out.writeLong(myParentLastReadStamp);
    out.writeLong(myProfilesTimestamp);
    out.writeLong(myUserSettingsTimestamp);
    out.writeLong(myGlobalSettingsTimestamp);
    out.writeLong(myExplicitProfilesHashCode);
    out.writeLong(myJvmConfigTimestamp);
    out.writeLong(myMavenConfigTimestamp);
  }

  @Override
  public String toString() {
    return "(" + myPomTimestamp
           + ":" + myParentLastReadStamp
           + ":" + myProfilesTimestamp
           + ":" + myUserSettingsTimestamp
           + ":" + myGlobalSettingsTimestamp
           + ":" + myExplicitProfilesHashCode
           + ":" + myJvmConfigTimestamp
           + ":" + myMavenConfigTimestamp + ")";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    MavenProjectTimestamp timestamp = (MavenProjectTimestamp)o;

    if (myPomTimestamp != timestamp.myPomTimestamp) return false;
    if (myParentLastReadStamp != timestamp.myParentLastReadStamp) return false;
    if (myProfilesTimestamp != timestamp.myProfilesTimestamp) return false;
    if (myUserSettingsTimestamp != timestamp.myUserSettingsTimestamp) return false;
    if (myGlobalSettingsTimestamp != timestamp.myGlobalSettingsTimestamp) return false;
    if (myExplicitProfilesHashCode != timestamp.myExplicitProfilesHashCode) return false;
    if (myJvmConfigTimestamp != timestamp.myJvmConfigTimestamp) return false;
    if (myMavenConfigTimestamp != timestamp.myMavenConfigTimestamp) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = 0;
    result = 31 * result + Long.hashCode(myPomTimestamp);
    result = 31 * result + Long.hashCode(myParentLastReadStamp);
    result = 31 * result + Long.hashCode(myProfilesTimestamp);
    result = 31 * result + Long.hashCode(myUserSettingsTimestamp);
    result = 31 * result + Long.hashCode(myGlobalSettingsTimestamp);
    result = 31 * result + Long.hashCode(myExplicitProfilesHashCode);
    result = 31 * result + Long.hashCode(myJvmConfigTimestamp);
    result = 31 * result + Long.hashCode(myMavenConfigTimestamp);
    return result;
  }
}
