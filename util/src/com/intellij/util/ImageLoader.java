/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.util;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NonNls;
import sun.reflect.Reflection;

import javax.swing.*;
import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.Serializable;
import java.net.URL;

@Deprecated
public class ImageLoader implements Serializable {
  private final static Component ourComponent = new Component() {};

  private static final Logger LOG = Logger.getInstance("#com.intellij.util.ImageLoader");

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

  public static Image loadFromResource(@NonNls String s) {
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
    URL url = aClass.getResource(s);
    return url != null ? loadFromURL(url) : null;
  }

  public static Image loadFromURL(final URL url) {
    try {

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

      Image image = Toolkit.getDefaultToolkit().createImage(outputStream.toByteArray());
      waitForImage(image);

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
