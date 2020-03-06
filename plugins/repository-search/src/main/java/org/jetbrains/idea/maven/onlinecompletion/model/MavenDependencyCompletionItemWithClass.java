// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.onlinecompletion.model;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

public class MavenDependencyCompletionItemWithClass extends MavenDependencyCompletionItem {

  private final Collection<String> myNames;

  public MavenDependencyCompletionItemWithClass(@Nullable String groupId,
                                                @Nullable String artifactId,
                                                @Nullable String version,
                                                @Nullable Type type,
                                                Collection<String> klassNames) {
    super(groupId, artifactId, version, type);
    myNames = klassNames;
  }

  public MavenDependencyCompletionItemWithClass(@Nullable String coord,
                                                @Nullable Type type,
                                                Collection<String> klassNames) {
    super(coord, type);
    myNames = klassNames;
  }

  public Collection<String> getNames() {
    return Collections.unmodifiableCollection(myNames);
  }
}
