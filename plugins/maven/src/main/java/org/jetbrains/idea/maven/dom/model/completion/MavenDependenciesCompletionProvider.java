// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom.model.completion;

/*need for plugin compatibility.*/

import org.jetbrains.annotations.ApiStatus;

/**
 * @deprecated use MavenDependenciesCompletionContributor instead
 */
@ApiStatus.ScheduledForRemoval(inVersion = "2019.3")
@Deprecated
public class MavenDependenciesCompletionProvider extends MavenDependenciesCompletionContributor {
}
