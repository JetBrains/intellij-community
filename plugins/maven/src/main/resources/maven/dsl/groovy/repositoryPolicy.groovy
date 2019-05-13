// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package maven.dsl.groovy

class repositoryPolicy {

  /**
   *
   *
   *             Whether to use this repository for downloading
   * this type of artifact. Note: While the type
   *             of this field is <code>String</code> for
   * technical reasons, the semantic type is actually
   *             <code>Boolean</code>. Default value is
   * <code>true</code>.
   *
   *
   */
  String enabled;

  /**
   *
   *
   *             The frequency for downloading updates - can be
   *             <code>always,</code>
   *             <code>daily</code>
   *             (default),
   *             <code>interval:XXX</code>
   *             (in minutes) or
   *             <code>never</code>
   *             (only if it doesn't exist locally).
   *
   *
   */
  String updatePolicy;

  /**
   *
   *
   *             What to do when verification of an artifact
   * checksum fails. Valid values are
   *             <code>ignore</code>
   *             ,
   *             <code>fail</code>
   *             or
   *             <code>warn</code>
   *             (the default).
   *
   *
   */
  String checksumPolicy;

  /**
   * What to do when verification of an artifact checksum
   * fails. Valid values are
   *             <code>ignore</code>
   *             ,
   *             <code>fail</code>
   *             or
   *             <code>warn</code>
   *             (the default).
   */
  void checksumPolicy(String checksumPolicy) {}

  /**
   * Whether to use this repository for downloading this type
   * of artifact. Note: While the type
   *             of this field is <code>String</code> for
   * technical reasons, the semantic type is actually
   *             <code>Boolean</code>. Default value is
   * <code>true</code>.
   */
  void enabled(String enabled) {}

  /**
   * The frequency for downloading updates - can be
   *             <code>always,</code>
   *             <code>daily</code>
   *             (default),
   *             <code>interval:XXX</code>
   *             (in minutes) or
   *             <code>never</code>
   *             (only if it doesn't exist locally).
   */
  void updatePolicy(String updatePolicy) {}

  void enabled(boolean enabled) {}
}
