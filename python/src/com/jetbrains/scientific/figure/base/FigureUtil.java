// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.scientific.figure.base;

import com.intellij.util.ui.JBUI;
import com.jetbrains.scientific.figure.FigureConstants;
import com.jetbrains.scientific.figure.WithBinaryContent;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class FigureUtil {
  @NotNull
  public static BufferedImage componentImage(@NotNull Component component) {
    return componentImage(component, component.getWidth(), component.getHeight());
  }

  @NotNull
  public static BufferedImage componentImage(@NotNull Component component, int width, int height) {
    //BufferedImage img = UIUtil.createImage(component, width, height, BufferedImage.TYPE_INT_ARGB);
    //noinspection UndesirableClassUsage (UIUtil creates broken image in case of retina)
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    Graphics2D graphics = image.createGraphics();
    component.paintAll(graphics);
    graphics.dispose();
    return image;
  }

  @NotNull
  public static Image fit(@NotNull Image image, int width, int height) {
    return image.getScaledInstance(JBUI.scale(width), JBUI.scale(height), Image.SCALE_SMOOTH);
  }

  public static byte[] componentToByteArray(@NotNull JComponent component) {
    byte[] bytes;
    if (component instanceof WithBinaryContent) {
      bytes = ((WithBinaryContent)component).getBytes();
    }
    else {
      bytes = toByteArray(componentImage(component));
    }
    return bytes;
  }

  public static byte[] toByteArray(@NotNull RenderedImage image) {
    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    try {
      ImageIO.write(image, FigureConstants.DEFAULT_IMAGE_FORMAT, stream);
      stream.flush();
      byte[] imageInByte = stream.toByteArray();
      stream.close();
      return imageInByte;
    }
    catch (IOException e) {
      throw new IllegalArgumentException("Failed to convert image to byte array", e);
    }
  }

  @NotNull
  public static BufferedImage fromRawBytes(int width, byte[] raw) {
    int height = raw.length / 3 / width;
    final DataBuffer buffer = new DataBufferByte(raw, raw.length);
    final SampleModel sampleModel = new ComponentSampleModel(DataBuffer.TYPE_BYTE, width, height, 3, width * 3,
                                                             new int[]{0, 1, 2});
    final Raster raster = Raster.createRaster(sampleModel, buffer, null);
    //noinspection UndesirableClassUsage (UIUtil creates broken image in case of retina)
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    image.setData(raster);
    return image;
  }
}
