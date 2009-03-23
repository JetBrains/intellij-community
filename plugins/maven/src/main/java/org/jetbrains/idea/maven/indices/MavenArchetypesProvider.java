package org.jetbrains.idea.maven.indices;

import com.intellij.openapi.extensions.ExtensionPointName;

import java.util.Collection;

public interface MavenArchetypesProvider {
  ExtensionPointName<MavenArchetypesProvider> EP_NAME = ExtensionPointName.create("org.jetbrains.idea.maven.archetypesProvider");

  Collection<ArchetypeInfo> getArchetypes();
}
