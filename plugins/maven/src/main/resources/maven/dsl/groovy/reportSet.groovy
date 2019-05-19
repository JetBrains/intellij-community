// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package maven.dsl.groovy

class reportSet {
    /**
   * The unique id for this report set, to be used during POM
   * inheritance and profile injection
   *             for merging of report sets.
   *
   */
  String id = "default";

  /**
   * Field reports.
   */
  List<String> reports;

  /**
   * The unique id for this report set, to be used during POM
   * inheritance and profile injection
   *             for merging of report sets.
   */
  void id(String id) {}

  /**
   * The list of reports from this plugin which should be
   * generated from this set.
   */
  void reports(List<String> reports) {}

  /**
   * The list of reports from this plugin which should be
   * generated from this set.
   */
  void reports(String... reports) {}
}
