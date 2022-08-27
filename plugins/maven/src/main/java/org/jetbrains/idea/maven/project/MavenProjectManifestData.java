// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project;

import java.util.Collection;

/**
 * A partial description of an OSGi-enabled Maven project's MANIFEST.
 * This information is needed in case Tycho support is on,
 * as we have to understand the relationships between local modules,
 * to pass the needed ones to the same reactor for the dependency resolution process.
 */
record MavenProjectManifestData(
  Collection<String> requiredBundles,
  Collection<String> importedPackages,
  Collection<String> exportedPackages) {
  //
}
