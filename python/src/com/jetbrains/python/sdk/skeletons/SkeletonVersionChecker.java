/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.sdk.skeletons;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.PythonHelpersLocator;
import org.jetbrains.annotations.NonNls;

import java.io.*;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses required_gen_version file.
 * Efficiently checks file versions against it.
 * Is immutable.
 * <br/>
 * User: dcheryasov
 */
public class SkeletonVersionChecker {
  private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.sdk.PythonSdkType.SkeletonVersionChecker");

  final static Pattern ONE_LINE = Pattern.compile("^(?:(\\w+(?:\\.\\w+)*|\\(built-in\\)|\\(default\\))\\s+(\\d+\\.\\d+))?\\s*(?:#.*)?$");

  public static final int PREGENERATED_VERSION = -1;

  @NonNls static final String REQUIRED_VERSION_FNAME = "required_gen_version";
  @NonNls static final String DEFAULT_NAME = "(default)"; // version required if a package is not explicitly mentioned
  @NonNls public static final String BUILTIN_NAME = "(built-in)"; // version required for built-ins
  @NonNls public static final String PREGENERATED = "(pre-generated)"; // pre-generated skeleton
  private TreeMap<QualifiedName, Integer> myExplicitVersion; // versions of regularly named packages
  private Integer myDefaultVersion; // version of (default)
  private Integer myBuiltinsVersion; // version of (built-it)

  /**
   * Creates an instance, loads requirements file.
   */
  public SkeletonVersionChecker(int defaultVersion) {
    myExplicitVersion = createTreeMap();
    myDefaultVersion = defaultVersion;
    load();
  }

  public boolean isPregenerated() {
    return myDefaultVersion == PREGENERATED_VERSION;
  }

  private static TreeMap<QualifiedName, Integer> createTreeMap() {
    return new TreeMap<>((left, right) -> {
      Iterator<String> lefts = left.getComponents().iterator();
      Iterator<String> rights = right.getComponents().iterator();
      while (lefts.hasNext() && rights.hasNext()) {
        int res = lefts.next().compareTo(rights.next());
        if (res != 0) return res;
      }
      if (lefts.hasNext()) return 1;
      if (rights.hasNext()) return -1;
      return 0;  // equal
    });
  }

  SkeletonVersionChecker(TreeMap<QualifiedName, Integer> explicit, Integer builtins) {
    myExplicitVersion = explicit;
    myBuiltinsVersion = builtins;
  }

  /**
   * @param version the new default version
   * @return a shallow copy of this with different default version.
   */
  public SkeletonVersionChecker withDefaultVersionIfUnknown(int version) {
    SkeletonVersionChecker ret = new SkeletonVersionChecker(myExplicitVersion, myBuiltinsVersion);
    ret.myDefaultVersion = myDefaultVersion != 0 ? myDefaultVersion : version;
    return ret;
  }

  private void load() {
    // load the required versions file
    File infile = PythonHelpersLocator.getHelperFile(REQUIRED_VERSION_FNAME);
    try {
      if (infile.canRead()) {
        Reader input = new FileReader(infile);
        LineNumberReader lines = new LineNumberReader(input);
        try {
          String line;
          do {
            line = lines.readLine();
            if (line != null) {
              Matcher matcher = ONE_LINE.matcher(line);
              if (matcher.matches()) {
                String package_name = matcher.group(1);
                String ver = matcher.group(2);
                if (package_name != null) {
                  final int version = fromVersionString(ver);
                  if (DEFAULT_NAME.equals(package_name)) {
                    if (myDefaultVersion != PREGENERATED_VERSION) {
                      myDefaultVersion = version;
                    }
                  }
                  else if (BUILTIN_NAME.equals(package_name)) {
                    myBuiltinsVersion = version;
                  }
                  else {
                    myExplicitVersion.put(QualifiedName.fromDottedString(package_name), version);
                  }
                } // else the whole line is a valid comment, and both catch groups are null
              }
              else LOG.warn(REQUIRED_VERSION_FNAME + ":" + lines.getLineNumber() + " Incorrect line, ignored" );
            }
          } while (line != null);
          if (myBuiltinsVersion == null) {
            myBuiltinsVersion = myDefaultVersion;
            LOG.warn("Assuming default version for built-ins");
          }
          assert (myDefaultVersion != null) : "Default version not known somehow!";
        }
        finally {
          lines.close();
        }
      }
    }
    catch (IOException e) {
      throw new LoadException(e);
    }
  }

  public int getRequiredVersion(String package_name) {
    QualifiedName qname = QualifiedName.fromDottedString(package_name);
    Map.Entry<QualifiedName,Integer> found = myExplicitVersion.floorEntry(qname);
    if (found != null && qname.matchesPrefix(found.getKey())) {
      return found.getValue();
    }
    return myDefaultVersion;
  }

  public int getBuiltinVersion() {
    if (myBuiltinsVersion == null) {
      myBuiltinsVersion = myDefaultVersion;
      // we could have started with no default and no builtins set, then default set by withDefaultVersionIfUnknown
    }
    return myBuiltinsVersion;
  }

  /**
   * Transforms a string like "1.2" into an integer representing it.
   * @param input
   * @return an int representing the version: major number shifted 8 bit and minor number added. or 0 if version can't be parsed.
   */
  public static int fromVersionString(final String input) {
    int dot_pos = input.indexOf('.');
    try {
      if (dot_pos > 0) {
        int major = Integer.parseInt(input.substring(0, dot_pos));
        int minor = Integer.parseInt(input.substring(dot_pos+1));
        return (major << 8) + minor;
      }
    }
    catch (NumberFormatException ignore) { }
    return 0;
  }

  public static String toVersionString(final int input) {
    int major = input >> 8;
    int minor = input - (major << 8);
    return String.valueOf(major) + "." + minor;
  }

  public static class LoadException extends RuntimeException {
    public LoadException(Throwable e) {
      super(e);
    }
  }
}
