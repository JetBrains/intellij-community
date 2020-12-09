// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface RemotePathTransformerFactory {
  ExtensionPointName<RemotePathTransformerFactory> MAVEN_REMOTE_PATH_TRANSFORMER_EP_NAME
    = new ExtensionPointName<>("org.jetbrains.idea.maven.remotePathTransformerFactory");

  static Transformer createForProject(@NotNull String projectPath) {
    RemotePathTransformerFactory[] transformers = MAVEN_REMOTE_PATH_TRANSFORMER_EP_NAME.getExtensions();
    List<RemotePathTransformerFactory> aTransformers = ContainerUtil.filter(transformers, factory -> factory.isApplicable(projectPath));
    if (aTransformers.size() > 1) {
      Logger.getInstance(RemotePathTransformerFactory.class).warn("More than one RemotePathTransformer is applicable: " + aTransformers);
    }

    return aTransformers.isEmpty() ? Transformer.ID : aTransformers.get(0).createTransformer(projectPath);
  }

  boolean isApplicable(@Nullable String projectPath);

  Transformer createTransformer(String projectFile);

  interface Transformer {
    Transformer ID = new Transformer() {
      @Override
      public String toRemotePath(String localPath) {
        return localPath;
      }

      @Override
      public String toIdePath(String remotePath) {
        return remotePath;
      }
    };

    String toRemotePath(String localPath);

    String toIdePath(String remotePath);
  }
}
