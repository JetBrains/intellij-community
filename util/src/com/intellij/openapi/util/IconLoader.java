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
package com.intellij.openapi.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.reference.SoftReference;
import com.intellij.util.ImageLoader;
import com.intellij.util.containers.WeakHashMap;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import sun.reflect.Reflection;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.ref.Reference;
import java.net.URL;
import java.util.Map;

public final class IconLoader {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.util.IconLoader");
  private static final Color ourTransparentColor = new Color(0, 0, 0, 0);

  private static final Map<URL, Icon> ourIconsCache = new THashMap<URL, Icon>(100, 0.9f);


  /**
   * This cache contains mapping between icons and disabled icons.
   */
  private static final Map<Icon, Icon> ourIcon2DisabledIcon = new WeakHashMap<Icon, Icon>(200);
  /**
   * To get disabled icon with paint it into the imag. Some icons require
   * not null component to paint.
   */
  private static final JComponent ourFakeComponent = new JLabel();
  private static final ImageIcon EMPTY_ICON = new ImageIcon(new BufferedImage(1, 1, BufferedImage.TYPE_3BYTE_BGR)) {
    @NonNls
    public String toString() {
      return "Empty icon " + super.toString();
    }
  };

  private static boolean ourIsActivated = false;

  private IconLoader() {
  }

  @Deprecated
  public static Icon getIcon(final Image image) {
    LOG.assertTrue(image != null);
    return new MyImageIcon(image);
  }

  public static Icon getIcon(@NonNls final String path) {
    int stackFrameCount = 2;
    Class callerClass = Reflection.getCallerClass(stackFrameCount);
    while (callerClass != null && callerClass.getClassLoader() == null) { // looks like a system class
      callerClass = Reflection.getCallerClass(++stackFrameCount);
    }
    if (callerClass == null) {
      callerClass = Reflection.getCallerClass(1);
    }
    return getIcon(path, callerClass);
  }


  public static Icon findIcon(@NonNls final String path) {
    int stackFrameCount = 2;
    Class callerClass = Reflection.getCallerClass(stackFrameCount);
    while (callerClass != null && callerClass.getClassLoader() == null) { // looks like a system class
      callerClass = Reflection.getCallerClass(++stackFrameCount);
    }
    if (callerClass == null) {
      callerClass = Reflection.getCallerClass(1);
    }
    return findIcon(path, callerClass);
  }

  public static Icon getIcon(final String path, final Class aClass) {
    final Icon icon = findIcon(path, aClass);
    if (icon == null) {
      LOG.error("Icon cannot be found in '" + path + "', aClass='" + (aClass == null ? null : aClass.getName()) + "'");
    }
    return icon;
  }

  public static void activate() {
    ourIsActivated = true;
  }

  private static boolean isLoaderDisabled() {
    return !ourIsActivated;
  }

  public static Icon findIcon(final String path, final Class aClass) {
    URL url = aClass.getResource(path);
    return findIcon(url);
  }

  @Nullable
  private static Icon findIcon(URL url) {
    if (url == null) return null;

    Icon icon = ourIconsCache.get(url);
    if (icon == null) {
      icon = new CachedImageIcon(url);
      ourIconsCache.put(url, icon);
    }
    return icon;
  }

  @Nullable
  public static Icon findIcon(final String path, final ClassLoader aClassLoader) {
    if (!path.startsWith("/")) return null;

    final URL url = aClassLoader.getResource(path.substring(1));
    return findIcon(url);
  }

  @Nullable
  private static Icon checkIcon(final Image image, final URL path) {
    if (image == null || image.getHeight(ourFakeComponent) < 1) { // image wasn't loaded or broken
      return null;
    }

    final Icon icon = getIcon(image);
    if (icon != null && !ImageLoader.isGoodSize(icon)) {
      LOG.error("Invalid icon: " + path); // # 22481
      return EMPTY_ICON;
    }
    return icon;
  }

  /**
   * Gets (creates if necessary) disabled icon based on the passed one.
   *
   * @return <code>ImageIcon</code> constructed from disabled image of passed icon.
   * @param icon
   */
  @Nullable
  public static Icon getDisabledIcon(final Icon icon) {
    if (icon == null) {
      return null;
    }
    Icon disabledIcon = ourIcon2DisabledIcon.get(icon);
    if (disabledIcon == null) {
      if (!ImageLoader.isGoodSize(icon)) {
        LOG.error(icon.toString()); // # 22481
        return EMPTY_ICON;
      }
      final BufferedImage image = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
      final Graphics2D graphics = image.createGraphics();

      graphics.setColor(ourTransparentColor);
      graphics.fillRect(0, 0, icon.getIconWidth(), icon.getIconHeight());
      icon.paintIcon(ourFakeComponent, graphics, 0, 0);

      graphics.dispose();

      disabledIcon = new MyImageIcon(GrayFilter.createDisabledImage(image));
      ourIcon2DisabledIcon.put(icon, disabledIcon);
    }
    return disabledIcon;
  }

  public static Icon getTransparentIcon(final Icon icon) {
    return getTransparentIcon(icon, 0.5f);
  }

  public static Icon getTransparentIcon(final Icon icon, final float alpha) {
    return new Icon() {
      public int getIconHeight() {
        return icon.getIconHeight();
      }

      public int getIconWidth() {
        return icon.getIconWidth();
      }

      public void paintIcon(final Component c, final Graphics g, final int x, final int y) {
        final Graphics2D g2 = (Graphics2D)g;
        final Composite saveComposite = g2.getComposite();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, alpha));
        icon.paintIcon(c, g2, x, y);
        g2.setComposite(saveComposite);
      }
    };
  }

  private static final class CachedImageIcon implements Icon {
    private URL myURL;
    private Reference<Icon> myRealIcon;

    public CachedImageIcon(URL myURL) {
      this.myURL = myURL;
    }

    private synchronized Icon getRealIcon() {
      if (isLoaderDisabled()) return EMPTY_ICON;

      Icon icon;
      if (myRealIcon != null) {
        icon = myRealIcon.get();
        if (icon != null) return icon;
      }

      Image image = ImageLoader.loadFromURL(myURL);
      icon = checkIcon(image, myURL);
      if (icon != null) {
        myRealIcon = new SoftReference<Icon>(icon);
      }
      return icon != null ? icon : EMPTY_ICON;
    }


    public void paintIcon(Component c, Graphics g, int x, int y) {
      getRealIcon().paintIcon(c, g, x, y);
    }

    public int getIconWidth() {
      return getRealIcon().getIconWidth();
    }

    public int getIconHeight() {
      return getRealIcon().getIconHeight();
    }
  }

  private static final class MyImageIcon extends ImageIcon {
    public MyImageIcon(final Image image) {
      super(image);
    }

    public final synchronized void paintIcon(final Component c, final Graphics g, final int x, final int y) {
      super.paintIcon(null, g, x, y);
    }
  }
}
