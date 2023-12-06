// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server;

import org.apache.maven.model.Model;
import org.apache.maven.project.inheritance.DefaultModelInheritanceAssembler;
import org.jetbrains.idea.maven.model.MavenModel;

public final class Maven3ModelInheritanceAssembler {
  public static MavenModel assembleInheritance(MavenModel model, MavenModel parentModel) {
    Model result = Maven3ModelConverter.toNativeModel(model);
    new DefaultModelInheritanceAssembler().assembleModelInheritance(result, Maven3ModelConverter.toNativeModel(parentModel));
    return Maven3ModelConverter.convertModel(result, null);
  }
}
