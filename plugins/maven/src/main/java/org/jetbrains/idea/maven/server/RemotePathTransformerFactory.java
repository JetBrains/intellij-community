// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public interface RemotePathTransformerFactory {
  ExtensionPointName<RemotePathTransformerFactory> MAVEN_REMOTE_PATH_TRANSFORMER_EP_NAME
    = new ExtensionPointName<>("org.jetbrains.idea.maven.remotePathTransformerFactory");

  RemotePathTransformerFactory ID = new RemotePathTransformerFactory() {
    @Override
    public Transformer createTransformer(String projectFile) {
      return Transformer.ID;
    }
  };

  static Transformer createForProject(@Nullable String projectPath) {
    RemotePathTransformerFactory[] transformers = MAVEN_REMOTE_PATH_TRANSFORMER_EP_NAME.getExtensions();
    if (transformers.length > 1) {
      throw new RuntimeException("More than one RemotePathTransformer is registered: " + Arrays.toString(transformers));
    }

    return transformers.length > 0 && projectPath != null
           ? transformers[0].createTransformer(projectPath)
           : Transformer.ID;
  }

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
