/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jetbrains.python.plots;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.testFramework.BinaryLightVirtualFile;
import com.intellij.util.ui.UIUtil;

import javax.imageio.ImageIO;
import java.awt.image.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class PyPlotVirtualFile extends BinaryLightVirtualFile {
  private static final Logger LOG = Logger.getInstance(PyPlotToolWindow.class);
  private BufferedImage myImage;

  public PyPlotVirtualFile(int width, byte[] raw) {
    super(PyPlotToolWindow.PLOT_DEFAULT_NAME + "." + PyPlotToolWindow.PLOT_FORMAT);

    int height = raw.length / 3 / width;
    final DataBuffer buffer = new DataBufferByte(raw, raw.length);
    final SampleModel sampleModel = new ComponentSampleModel(DataBuffer.TYPE_BYTE, width, height, 3, width * 3,
                                                             new int[]{0, 1, 2});
    final Raster raster = Raster.createRaster(sampleModel, buffer, null);
    myImage = UIUtil.createImage(width, height, BufferedImage.TYPE_INT_RGB);
    if (SystemInfo.isMac && UIUtil.isRetina()) {
      myImage = myImage.getSubimage(0, 0, width / 2, height / 2);
    }
    myImage.setData(raster);
    byte[] bytes = imageToByteArray(myImage);
    if (bytes == null) return;
    ApplicationManager.getApplication().invokeLater(() -> {
      ApplicationManager.getApplication().runWriteAction(() -> {
        try {
          setBinaryContent(bytes);
        }
        catch (IOException ignored) {
        }
      });
    });
  }

  public BufferedImage getImage() {
    return myImage;
  }

  public static byte[] imageToByteArray(BufferedImage image) {
    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    try {
      ImageIO.write(image, PyPlotToolWindow.PLOT_FORMAT, stream);
      stream.flush();
      byte[] imageInByte = stream.toByteArray();
      stream.close();
      return imageInByte;
    }
    catch (IOException e) {
      LOG.warn("Failed to convert image to byte array " + e.getMessage());
    }
    return null;
  }
}
