// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package maven.dsl.groovy

class repository {
  /**
   *
   *
   *             A unique identifier for a repository. This is
   * used to match the repository
   *             to configuration in the
   * <code>settings.xml</code> file, for example. Furthermore,
   * the identifier is
   *             used during POM inheritance and profile
   * injection to detect repositories that should be merged.
   *
   *
   */
  String id;

  /**
   * Human readable name of the repository.
   */
  String name;

  /**
   *
   *
   *             The url of the repository, in the form
   * <code>protocol://hostname/path</code>.
   *
   *
   */
  String url;

  /**
   *
   *
   *             The type of layout this repository uses for
   * locating and storing artifacts -
   *             can be <code>legacy</code> or
   * <code>default</code>.
   *
   *
   */
  String layout = "default";
  /**
   * A unique identifier for a repository. This is used to
   * match the repository
   *             to configuration in the
   * <code>settings.xml</code> file, for example. Furthermore,
   * the identifier is
   *             used during POM inheritance and profile
   * injection to detect repositories that should be merged.
   */
  void id(String id) {}

  /**
   * The type of layout this repository uses for locating and
   * storing artifacts -
   *             can be <code>legacy</code> or
   * <code>default</code>.
   */
  void layout(String layout) {}

  /**
   * Human readable name of the repository.
   */
  void name(String name) {}

  /**
   * The url of the repository, in the form
   * <code>protocol://hostname/path</code>.
   */
  void url(String url) {}

  /**
   * How to handle downloading of releases from this repository.
   */
  void releases(Closure closure) {}
  /**
   * How to handle downloading of snapshots from this repository.
   */
  void snapshots(Closure closure) {}
}
