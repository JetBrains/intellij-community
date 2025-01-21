// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution.run;

import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.execution.configurations.RemoteConnectionCreator;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.execution.MavenRemoteConnectionCreator;
import org.jetbrains.idea.maven.execution.MavenRunConfiguration;

public class MavenExtRemoteConnectionCreator implements RemoteConnectionCreator {
  private static final ExtensionPointName<MavenRemoteConnectionCreator> EP_NAME =
    ExtensionPointName.create("org.jetbrains.idea.maven.mavenRemoteConnectionCreator");
  private final JavaParameters myJavaParameters;
  private final MavenRunConfiguration myRunConfiguration;

  public MavenExtRemoteConnectionCreator(JavaParameters javaParameters, MavenRunConfiguration runConfiguration) {
    myJavaParameters = javaParameters;
    myRunConfiguration = runConfiguration;
  }

  @Override
  public @Nullable RemoteConnection createRemoteConnection(ExecutionEnvironment environment) {
    for (MavenRemoteConnectionCreator creator : EP_NAME.getExtensionList()) {
      RemoteConnection connection = creator.createRemoteConnection(myJavaParameters, myRunConfiguration);
      if (connection != null) {
        return connection;
      }
    }
    return null;
  }

  @Override
  public boolean isPollConnection() {
    return true;
  }
}
