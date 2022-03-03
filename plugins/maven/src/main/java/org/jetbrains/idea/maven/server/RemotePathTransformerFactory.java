// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Extension point to transform local maven project path to remote one and vice versa.
 */
public interface RemotePathTransformerFactory {
  ExtensionPointName<RemotePathTransformerFactory> MAVEN_REMOTE_PATH_TRANSFORMER_EP_NAME
    = new ExtensionPointName<>("org.jetbrains.idea.maven.remotePathTransformerFactory");

  static Transformer createForProject(@NotNull Project project) {
    RemotePathTransformerFactory[] transformers = MAVEN_REMOTE_PATH_TRANSFORMER_EP_NAME.getExtensions();
    List<RemotePathTransformerFactory> aTransformers = ContainerUtil.filter(transformers, factory -> factory.isApplicable(project));
    if (aTransformers.size() > 1) {
      Logger.getInstance(RemotePathTransformerFactory.class).warn("More than one RemotePathTransformer is applicable: " + aTransformers);
    }

    return aTransformers.isEmpty() ? Transformer.ID : aTransformers.get(0).createTransformer(project);
  }

  boolean isApplicable(@NotNull Project project);

  /**
   * Create bidirectional path transformer for project.
   */
  Transformer createTransformer(@NotNull Project project);

  interface Transformer {
    Transformer ID = new Transformer() {
      @Override
      public @Nullable String toRemotePath(@NotNull String localPath) {
        return localPath;
      }

      @Override
      public @Nullable String toIdePath(@NotNull String remotePath) {
        return remotePath;
      }

      @Override
      public boolean canBeRemotePath(String s) {
        return false;
      }
    };

    @Nullable String toRemotePath(@NotNull String localPath);

    @Nullable String toIdePath(@NotNull String remotePath);

    boolean canBeRemotePath(String s);

    @Contract("!null -> !null")
    @Nullable
    default String toRemotePathOrSelf(@Nullable String localPath) {
      if (localPath == null) return null;
      String remotePath = toRemotePath(localPath);
      return remotePath != null ? remotePath : localPath;
    }
  }
}
