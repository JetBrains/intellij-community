// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server;

public class MavenConfigParseException extends RuntimeException {
  private final String myDirectory;

  public MavenConfigParseException(String message, String workingDirectory) {
    super(message);
    myDirectory = workingDirectory;
  }

  public String getDirectory() {
    return myDirectory;
  }
}
