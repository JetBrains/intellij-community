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
package org.jetbrains.idea.svn;

import java.io.File;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 2/5/13
 * Time: 4:56 PM
 */
public class SvnBindUtil {
  /**
   * SVN_ASP_DOT_NET_HACK allows use of an alternate name for Subversion working copy
   * administrative directories on Windows (which were formerly always
   * named ".svn"), by setting the SVN_ASP_DOT_NET_HACK environment variable.
   * When the variable is set (to any value), the administrative directory
   * will be "_svn" instead of ".svn".
   *
   * http://svn.apache.org/repos/asf/subversion/trunk/notes/asp-dot-net-hack.txt
   */
  public static final String ADM_NAME = System.getenv("SVN_ASP_DOT_NET_HACK") != null ? "_svn" : ".svn";
  private final static List<DateFormat> ourFormats = new ArrayList<DateFormat>();

  static {
    ourFormats.add(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS"));
    ourFormats.add(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'000Z'"));
    ourFormats.add(new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss z", Locale.US));
    ourFormats.add(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z (EE, d MMM yyyy)", Locale.getDefault()));
    ourFormats.add(new SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss' 'ZZZZ' ('E', 'dd' 'MMM' 'yyyy')'"));
    ourFormats.add(new SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss'Z'"));
    ourFormats.add(new SimpleDateFormat("EEE' 'MMM' 'dd' 'HH:mm:ss' 'yyyy"));
    ourFormats.add(new SimpleDateFormat("MM' 'dd'  'yyyy"));
    ourFormats.add(new SimpleDateFormat("MM' 'dd'  'HH:mm"));
    ourFormats.add(new SimpleDateFormat("MM' 'dd'  'HH:mm:ss"));
  }

  public static Date parseDate(final String date) {
    for (DateFormat format : ourFormats) {
      try {
        return format.parse(date);
      }
      catch (ParseException e) {
        continue;
      }
    }
    return new Date(0);
  }

  public static void changelistsToCommand(String[] changeLists, final List<String> list) {
    if (changeLists != null) {
      for (String name : changeLists) {
        list.add("--cl");
        list.add(name);
      }
    }
  }

  public static String getDepthName(final int i) {
    return org.tigris.subversion.javahl.Depth.toADepth(i).name();
  }

  public static File correctUpToExistingParent(File base) {
    while (base != null) {
      if (base.exists() && base.isDirectory()) return base;
      base = base.getParentFile();
    }
    return null;
  }

  public static File getWcRoot(File base) {
    File current = base;
    while (current != null) {
      if (getWcDbUnder(current).exists()) return current;
      current = current.getParentFile();
    }
    return null;
  }

  public static File getWcDbUnder(final File file) {
    return new File(file, ADM_NAME + File.separator + "wc.db");
  }
}
