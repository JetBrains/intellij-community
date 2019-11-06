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
import com.jetbrains.python.sdk.skeleton.PySkeletonHeader;

import java.io.*;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;

/**
 * Parses required_gen_version file.
 * Efficiently checks file versions against it.
 * Is immutable.
 * <br/>
 * User: dcheryasov
 */
public class SkeletonVersionChecker {
  private static final Logger LOG = Logger.getInstance(SkeletonVersionChecker.class);

  private final TreeMap<QualifiedName, Integer> myExplicitVersion; // versions of regularly named packages
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
    return myDefaultVersion == PySkeletonHeader.PREGENERATED_VERSION;
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
    File infile = PythonHelpersLocator.getHelperFile(PySkeletonHeader.REQUIRED_VERSION_FNAME);
    try {
      if (infile.canRead()) {
        Reader input = new FileReader(infile);
        LineNumberReader lines = new LineNumberReader(input);
        try {
          String line;
          do {
            line = lines.readLine();
            if (line != null) {
              Matcher matcher = PySkeletonHeader.ONE_LINE.matcher(line);
              if (matcher.matches()) {
                String package_name = matcher.group(1);
                String ver = matcher.group(2);
                if (package_name != null) {
                  final int version = PySkeletonHeader.fromVersionString(ver);
                  if (PySkeletonHeader.DEFAULT_NAME.equals(package_name)) {
                    if (myDefaultVersion != PySkeletonHeader.PREGENERATED_VERSION) {
                      myDefaultVersion = version;
                    }
                  }
                  else if (PySkeletonHeader.BUILTIN_NAME.equals(package_name)) {
                    myBuiltinsVersion = version;
                  }
                  else {
                    myExplicitVersion.put(QualifiedName.fromDottedString(package_name), version);
                  }
                } // else the whole line is a valid comment, and both catch groups are null
              }
              else LOG.warn(PySkeletonHeader.REQUIRED_VERSION_FNAME + ":" + lines.getLineNumber() + " Incorrect line, ignored" );
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

  public static String toVersionString(final int input) {
    int major = input >> 8;
    int minor = input - (major << 8);
    return major + "." + minor;
  }

  public static class LoadException extends RuntimeException {
    public LoadException(Throwable e) {
      super(e);
    }
  }
}
