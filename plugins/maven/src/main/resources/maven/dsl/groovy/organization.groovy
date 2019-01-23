// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package maven.dsl.groovy

class organization {
  /**
   * The full name of the organization.
   */
  String name;

  /**
   * The URL to the organization's home page.
   */
  String url;

  /**
   * Set the full name of the organization.
   */
  void name(String name) {}

  /**
   * Set the URL to the organization's home page.
   */
  void url(String url) {}
}
