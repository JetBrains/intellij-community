// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.config;

import org.apache.commons.cli.Option;

import java.util.function.Function;

/**
 * partial copy of {@link org.apache.maven.cli.CLIManager}
 */
public enum MavenConfigSettings {
  OFFLINE("o", "offline", Function.identity()),
  UPDATE_SNAPSHOTS("U", "update-snapshots", Function.identity()),
  NON_RECURSIVE("N", "non-recursive", Function.identity()),
  QUIET("q", "quiet", Function.identity()),
  DEBUG("X", "debug", Function.identity()),
  ERRORS("e", "errors", Function.identity()),
  CHECKSUM_FAILURE_POLICY("C", "strict-checksums", Function.identity()),
  CHECKSUM_WARNING_POLICY("c", "lax-checksums", Function.identity()),
  FAIL_FAST("ff", "fail-fast", Function.identity()),
  FAIL_AT_END("fae", "fail-at-end", Function.identity()),
  FAIL_NEVER("fn", "fail-never", Function.identity()),
  THREADS("T", "threads", b -> b.hasArg()),
  ALTERNATE_USER_SETTINGS("s", "settings", b -> b.hasArg()),
  ALTERNATE_GLOBAL_SETTINGS("gs", "global-settings", b -> b.hasArg());

  final String key;
  final String longKey;
  final Function<Option.Builder, Option.Builder> builderFunction;

  MavenConfigSettings(String key, String longKey, Function<Option.Builder, Option.Builder> builderFunction) {
    this.key = key;
    this.longKey = longKey;
    this.builderFunction = builderFunction;
  }

  Option toOption() {
    return builderFunction.apply(Option.builder(key).longOpt(longKey)).build();
  }

  public String getKey() {
    return "-" + key;
  }

  public String getLongKey() {
    return "--" + longKey;
  }
}
