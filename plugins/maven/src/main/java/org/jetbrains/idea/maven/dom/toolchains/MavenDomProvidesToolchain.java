// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom.toolchains;

import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.Required;
import org.jetbrains.idea.maven.dom.MavenDomElement;

public interface MavenDomProvidesToolchain extends MavenDomElement {
  @Required
  GenericDomValue<String> getVersion();
  
  
}
