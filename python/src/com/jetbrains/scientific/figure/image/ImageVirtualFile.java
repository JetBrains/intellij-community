// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.scientific.figure.image;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.testFramework.BinaryLightVirtualFile;
import com.jetbrains.scientific.figure.base.FigureUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.image.BufferedImage;
import java.io.IOException;

public class ImageVirtualFile extends BinaryLightVirtualFile implements Disposable {
  private BufferedImage myImage;

  static ImageVirtualFile makeCopy(@NotNull ImageVirtualFile virtualFile) {
    return new ImageVirtualFile(virtualFile.getName(), virtualFile.getImage());
  }

  public ImageVirtualFile(String simpleFilename, @NotNull BufferedImage image) {
    super(simpleFilename);
    myImage = image;
    runSetBinaryContentAction(FigureUtil.toByteArray(myImage));
  }

  private void runSetBinaryContentAction(byte[] bytes) {
    if (bytes == null) return;
    ApplicationManager.getApplication().invokeLater(() -> ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        setBinaryContent(bytes);
      }
      catch (IOException ignored) {
      }
    }));
  }

  public BufferedImage getImage() {
    return myImage;
  }

  @Override
  public void dispose() {
    myImage = null;
  }
}
