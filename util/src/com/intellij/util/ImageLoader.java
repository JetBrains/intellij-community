/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util;

import com.intellij.openapi.diagnostic.Logger;
import gnu.trove.THashMap;
import sun.reflect.Reflection;

import javax.swing.*;
import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.Serializable;
import java.net.URL;
import java.util.Map;

public class ImageLoader implements Serializable {
  private final static Component ourComponent = new Component() {};

  private static final Logger LOG = Logger.getInstance("#com.intellij.util.ImageLoader");

  private static Map myUrl2Image = new THashMap(48, 0.9f);

  private static boolean waitForImage(Image image) {
    if (image == null) return false;
    if (image.getWidth(null) > 0) return true;
    MediaTracker mediatracker = new MediaTracker(ourComponent);
    mediatracker.addImage(image, 1);
    try {
      mediatracker.waitForID(1, 5000);
    } catch (InterruptedException ex) {
      LOG.info(ex);
    }
    return !mediatracker.isErrorID(1);
  }

  public static Image loadFromResource(String s) {
    int stackFrameCount = 2;
    Class callerClass = Reflection.getCallerClass(stackFrameCount);
    while (callerClass != null && callerClass.getClassLoader() == null) { // looks like a system class
      callerClass = Reflection.getCallerClass(++stackFrameCount);
    }
    if (callerClass == null) {
      callerClass = Reflection.getCallerClass(1);
    }
    return loadFromResource(s, callerClass);
  }

  public static Image loadFromResource(String s, Class aClass) {
    try {
      URL url = aClass.getResource(s);
      if (url == null) {
        return null;
      }
      Image image = (Image)myUrl2Image.get(url.toString());
      if (image != null) {
        return image;
      }

      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      final InputStream inputStream = url.openStream();
      try {
        byte[] buffer = new byte[1024];
        while (true) {
          final int n = inputStream.read(buffer);
          if (n < 0) break;
          outputStream.write(buffer, 0, n);
        }
      }
      finally {
        inputStream.close();
      }

      image = Toolkit.getDefaultToolkit().createImage(outputStream.toByteArray());
      waitForImage(image);

      myUrl2Image.put(url.toString(), image);
      return image;
    } catch (Exception ex) {
      LOG.error(ex);
    }

    return null;
  }

  public static boolean isGoodSize(Icon icon) {
    return icon.getIconWidth() > 0 && icon.getIconHeight() > 0;
  }
}
