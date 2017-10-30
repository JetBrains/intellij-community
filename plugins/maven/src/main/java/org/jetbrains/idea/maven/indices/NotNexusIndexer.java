// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.indices;

import org.jetbrains.idea.maven.server.MavenIndicesProcessor;
import org.jetbrains.idea.maven.server.MavenServerIndexerException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;

import java.io.IOException;

/**
 * @author ibessonov
 */
interface NotNexusIndexer {

  void processArtifacts(MavenProgressIndicator progress, MavenIndicesProcessor processor) throws IOException, MavenServerIndexerException;
}
