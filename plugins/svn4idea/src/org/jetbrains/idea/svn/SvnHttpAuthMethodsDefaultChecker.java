/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

public class SvnHttpAuthMethodsDefaultChecker {
  private static final String AUTH_METHODS_PROPERTY = "svnkit.http.methods";
  private static final String OLD_AUTH_METHODS_PROPERTY = "javasvn.http.methods";

  public static void check() {
    final String priorities = System.getProperty(AUTH_METHODS_PROPERTY, System.getProperty(OLD_AUTH_METHODS_PROPERTY));
    if (priorities == null) {
      System.setProperty(AUTH_METHODS_PROPERTY, "Basic,Digest,NTLM");
    }
  }
}
