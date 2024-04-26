// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.indices;


import org.jetbrains.annotations.ApiStatus;

/**
 * Consider using MavenGAVIndex or MavenSearchIndex instead
 */
@ApiStatus.Obsolete
public interface MavenIndex extends MavenGAVIndex, MavenArchetypeContainer, MavenUpdatableIndex {

}
