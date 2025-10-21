// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server;

import java.io.Serializable;

public class DownloadArtifactEvent implements Serializable {
  private final String myFile;

  public DownloadArtifactEvent(String file) {
    myFile = file;
  }

  public String getFile() {
    return myFile;
  }
}
