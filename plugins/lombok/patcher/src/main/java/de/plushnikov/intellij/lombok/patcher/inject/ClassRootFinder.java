/*
 * Copyright (C) 2014 The Project Lombok Authors.
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package de.plushnikov.intellij.lombok.patcher.inject;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.Charset;

public class ClassRootFinder {
  private static String urlDecode(String in, boolean forceUtf8) {
    try {
      return URLDecoder.decode(in, forceUtf8 ? "UTF-8" : Charset.defaultCharset().name());
    } catch (UnsupportedEncodingException e) {
      try {
        return URLDecoder.decode(in, "UTF-8");
      } catch (UnsupportedEncodingException e1) {
        return in;
      }
    }
  }

  public static String findClassRootOfSelf() {
    return findClassRootOfClass(ClassRootFinder.class);
  }

  public static String findClassRootOfClass(Class<?> context) {
    String name = context.getName();
    int idx = name.lastIndexOf('.');

    String packageBase = "";
    if (idx > -1) {
      packageBase = name.substring(0, idx);
      name = name.substring(idx + 1);
    }

    URL selfURL = context.getResource(name + ".class");
    String self = selfURL.toString();
    if (self.startsWith("file:/")) {

      String path = urlDecode(self.substring(5), false);
      if (!new File(path).exists()) {
        path = urlDecode(self.substring(5), true);
      }

      String suffix = "/" + packageBase.replace('.', '/') + "/" + name + ".class";
      if (!path.endsWith(suffix)) {
        throw new IllegalArgumentException("Unknown path structure: " + path);
      }

      self = path.substring(0, path.length() - suffix.length());
    } else if (self.startsWith("jar:")) {

      int sep = self.indexOf('!');
      if (sep == -1) {
        throw new IllegalArgumentException("No separator in jar protocol: " + self);
      }

      String jarLoc = self.substring(4, sep);
      if (jarLoc.startsWith("file:/")) {
        String path = urlDecode(jarLoc.substring(5), false);
        if (!new File(path).exists()) {
          path = urlDecode(jarLoc.substring(5), true);
        }
        self = path;
      } else {
        throw new IllegalArgumentException("Unknown path structure: " + self);
      }
    } else {
      throw new IllegalArgumentException("Unknown protocol: " + self);
    }

    if (self.isEmpty()) {
      self = "/";
    }

    // Attempt to fix system differences with pathing
    try {
      File file = new File(self);
      if (file.exists()) {
        self = file.getCanonicalPath();
      }
    } catch (IOException ex) {
      throw new IllegalArgumentException("Failed to resolve to a canonical path: " + self);
    }

    return self;
  }

  public static void main(String[] args) {
    System.out.println(findClassRootOfSelf());
  }
}
