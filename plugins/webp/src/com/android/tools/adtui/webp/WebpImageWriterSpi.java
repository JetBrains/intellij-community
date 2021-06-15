/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.adtui.webp;


import com.google.webp.libwebp;
import org.jetbrains.annotations.NotNull;

import javax.imageio.*;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.spi.ImageWriterSpi;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.*;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteOrder;
import java.util.Iterator;
import java.util.Locale;

/**
 * Encoder for WebP. This needs the webp jni library loaded to function.
 */
public class WebpImageWriterSpi extends ImageWriterSpi {
  WebpImageWriterSpi() {
    vendorName = WebpMetadata.WEBP_VENDOR;
    version = WebpNativeLibHelper.getEncoderVersion();
    suffixes = WebpMetadata.WEBP_SUFFIXES;
    names = WebpMetadata.WEBP_FORMAT_NAMES;
    MIMETypes = WebpMetadata.WEBP_MIME_TYPES;
    pluginClassName = WebpWriter.class.getName();
    outputTypes = new Class<?>[]{ImageOutputStream.class};
  }

  public static void writeImage(@NotNull BufferedImage image,
                                @NotNull OutputStream outputStream,
                                boolean lossless,
                                int quality) throws IOException {
    // Instead of
    //   ImageIO.write(image, WebpMetadata.WEBP_FORMAT_LOWER_CASE, stream);
    // which will pass null as the parameters to the ImageWriter, call through to the ImageWriter
    // more directly so we can set up the proper parameters:
    ImageTypeSpecifier type = ImageTypeSpecifier.createFromRenderedImage(image);
    Iterator<ImageWriter> iterator = ImageIO.getImageWriters(type, WebpMetadata.WEBP_FORMAT_LOWER_CASE);
    if (iterator.hasNext()) {
      ImageWriter writer = iterator.next();
      try (ImageOutputStream stream = ImageIO.createImageOutputStream(outputStream)) {
        writer.setOutput(stream);
        try {
          ImageWriteParam param = new WebpImageWriteParam(writer.getLocale(), !lossless, quality / 100f);
          writer.write(null, new IIOImage(image, null, null), param);
        }
        finally {
          writer.dispose();
          stream.flush();
        }
      }
    }
  }

  @Override
  public boolean canEncodeImage(ImageTypeSpecifier type) {
    return canWriteImage(type);
  }

  public static boolean canWriteImage(ImageTypeSpecifier type) {
    SampleModel sm = type.getSampleModel();
    ColorModel cm = type.getColorModel();
    return (sm.getNumBands() >= 2 && sm.getNumBands() <= 4 || cm instanceof IndexColorModel) &&
           sm.getSampleSize(0) <= 8 &&
           sm.getWidth() <= 65535 &&
           sm.getHeight() <= 65535 &&
           (cm == null || cm.getComponentSize()[0] <= 8) &&
           WebpNativeLibHelper.loadNativeLibraryIfNeeded();
  }

  @Override
  public ImageWriter createWriterInstance(Object extension) throws IOException {
    WebpNativeLibHelper.requireNativeLibrary();
    return new WebpWriter(this);
  }

  @Override
  public String getDescription(Locale locale) {
    return "WebP Image Encoder";
  }

  private static class WebpWriter extends ImageWriter {
    public WebpWriter(ImageWriterSpi originatingProvider) {
      super(originatingProvider);
    }

    @Override
    public IIOMetadata getDefaultStreamMetadata(ImageWriteParam param) {
      return new WebpMetadata();
    }

    @Override
    public IIOMetadata getDefaultImageMetadata(ImageTypeSpecifier imageType, ImageWriteParam param) {
      return new WebpMetadata();
    }

    @Override
    public IIOMetadata convertStreamMetadata(IIOMetadata inData, ImageWriteParam param) {
      return null;
    }

    @Override
    public IIOMetadata convertImageMetadata(IIOMetadata inData, ImageTypeSpecifier imageType, ImageWriteParam param) {
      return null;
    }

    private ImageOutputStream stream = null;

    @Override
    public void setOutput(Object output) {
      super.setOutput(output);
      if (output != null) {
        if (!(output instanceof ImageOutputStream)) {
          throw new
            IllegalArgumentException("output is not an ImageOutputStream");
        }
        this.stream = (ImageOutputStream)output;
        this.stream.setByteOrder(ByteOrder.LITTLE_ENDIAN);
      }
      else {
        this.stream = null;
      }
    }


    @Override
    public void write(IIOMetadata streamMetadata, IIOImage image, ImageWriteParam param) throws IOException {
      if (stream == null) {
        throw new IllegalStateException("output == null!");
      }
      boolean lossless = WebpMetadata.DEFAULT_LOSSLESS;
      float quality = WebpMetadata.DEFAULT_ENCODING_QUALITY;
      if (param != null && param.canWriteCompressed()) {
        lossless = false;
        if (param.getCompressionMode() == ImageWriteParam.MODE_EXPLICIT) {
          quality = param.getCompressionQuality();
        }
      }

      Raster srcRas = getRaster(image);

      int minX = srcRas.getMinX();
      int minY = srcRas.getMinY();
      int width = srcRas.getWidth();
      int height = srcRas.getHeight();
      int maxX = minX + width;
      int maxY = minY + height;

      byte[] data = new byte[width * height * 4];
      // TODO: This code is the inefficient fallback dealing with any color/sample model.
      // However, we've restricted things in the canEncodeImage method so we might as well try
      // to do it more efficiently
      int index = 0;
      SampleModel sampleModel = srcRas.getSampleModel();
      int bands = sampleModel.getNumBands();
      if (bands == 1) {
        // Indexed image but not found in raster
        return;
      }
      assert bands >= 2 && bands <= 4; // enforced in canEncodeImage
      DataBuffer buffer = srcRas.getDataBuffer();
      if (bands >= 3) {
        for (int y = minY; y < maxY; y++) {
          for (int x = minX; x < maxX; x++) {
            for (int band = 0; band < bands; band++) {
              int sample = sampleModel.getSample(x, y, band, buffer);
              data[index++] = (byte)sample;
            }
            if (bands == 3) {
              data[index++] = (byte)255; // implicit alpha in an RGB (not RGBA) image
            }
          }
        }
      }
      else {
        assert bands == 2;
        for (int y = minY; y < maxY; y++) {
          for (int x = minX; x < maxX; x++) {
            byte sample = (byte)sampleModel.getSample(x, y, 0, buffer);
            data[index++] = sample;
            data[index++] = sample;
            data[index++] = sample;
            byte alpha = (byte)sampleModel.getSample(x, y, 1, buffer);
            data[index++] = alpha;
          }
        }
      }

      // See https://developers.google.com/speed/webp/docs/api
      byte[] encoded;
      if (lossless) {
        encoded = libwebp.WebPEncodeLosslessRGBA(data, width, height, 4 * width);
      }
      else {
        encoded = libwebp.WebPEncodeRGBA(data, width, height, 4 * width, (int)(100 * quality));
      }
      stream.write(encoded);
    }

    @NotNull
    private static Raster getRaster(@NotNull IIOImage image) {
      boolean rasterOnly = image.hasRaster();
      if (rasterOnly) {
        return image.getRaster();
      }
      else {
        RenderedImage renderedImage = image.getRenderedImage();
        if (renderedImage instanceof BufferedImage) {
          // Convert indexed to RGB
          BufferedImage bufferedImage = (BufferedImage)renderedImage;
          if (renderedImage.getColorModel() instanceof IndexColorModel) {
            //noinspection UndesirableClassUsage
            BufferedImage rgb = new BufferedImage(bufferedImage.getWidth(), bufferedImage.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = rgb.createGraphics();
            graphics.drawImage(bufferedImage, 0, 0, null);
            graphics.dispose();
            return rgb.getRaster();
          }
          return bufferedImage.getRaster();
        }
        else if (renderedImage.getNumXTiles() == 1 &&
                 renderedImage.getNumYTiles() == 1) {
          Raster raster = renderedImage.getTile(renderedImage.getMinTileX(),
                                                renderedImage.getMinTileY());
          if (raster.getWidth() != renderedImage.getWidth() ||
              raster.getHeight() != renderedImage.getHeight()) {
            raster = raster.createChild(raster.getMinX(),
                                        raster.getMinY(),
                                        renderedImage.getWidth(),
                                        renderedImage.getHeight(),
                                        raster.getMinX(),
                                        raster.getMinY(),
                                        null);
          }
          return raster;
        }
        else {
          return renderedImage.getData();
        }
      }
    }
  }
}
