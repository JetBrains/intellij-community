/*
 * Copyright (c) 2003, 2010, Dave Kriewall
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 * 1) Redistributions of source code must retain the above copyright notice, this list of conditions and the following
 * disclaimer.
 *
 * 2) Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.wrq.rearranger.util;

import com.intellij.openapi.diagnostic.Logger;
import com.wrq.rearranger.Rearranger;

import javax.swing.*;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

/** Utility methods to obtain an icon from a resource or file. */
public class IconUtil {
// ------------------------------ FIELDS ------------------------------

  private static final Logger LOG = Logger.getInstance("#" + IconUtil.class.getName());

// -------------------------- STATIC METHODS --------------------------


  public static final Icon getIcon(String iconName) {
    URL iconURL = Rearranger.class.getClassLoader().getResource("com/wrq/rearranger/" + iconName + ".png");
    if (iconURL == null) {
      if (true) {
//            // for debugging only;
//            // in development, there's no resource (no jar file built),
//            // so go to the file directly.
        try {
          URLClassLoader cl = new URLClassLoader(
            new URL[]
            {new File("/rearranger/lib/rearranger.jar").toURL(),
              new File("/IntelliJ-IDEA-4.0/lib/icons.jar").toURL()}
          );
          iconURL = cl.getResource("com/wrq/rearranger/" + iconName + ".png");
          if (iconURL == null) {
            iconURL = cl.getResource(iconName + ".png");
          }
        }
        catch (MalformedURLException e) {
          iconURL = null;
        }
      }
    }
    if (iconURL != null) {
      final ImageIcon imageIcon = new ImageIcon(iconURL, iconName);
      return imageIcon;
    }
    LOG.debug(
      "getIcon: getResource() could not locate URL for com/wrq/rearranger/" +
      iconName +
      ".png or " +
      iconName + ".png in icons.jar"
    );
    return null;
  }
}
