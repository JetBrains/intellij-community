// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom.toolchains;

import com.intellij.openapi.paths.PathReference;
import com.intellij.util.xml.Convert;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.Required;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenDomElement;
import org.jetbrains.idea.maven.dom.references.MavenSourceDirectoryConverter;

public interface MavenDomConfigurationToolchain extends MavenDomElement {
  @NotNull
  @Required
  @Convert(value = MavenSourceDirectoryConverter.class, soft = false)
  GenericDomValue<PathReference> getJdkHome();
}
