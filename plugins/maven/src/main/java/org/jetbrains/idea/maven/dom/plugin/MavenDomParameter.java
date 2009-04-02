package org.jetbrains.idea.maven.dom.plugin;

import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenDomElement;

public interface MavenDomParameter extends MavenDomElement {
  @NotNull
  GenericDomValue<String> getName();

  @NotNull
  GenericDomValue<String> getAlias();

  @NotNull
  GenericDomValue<String> getType();

  @NotNull
  GenericDomValue<Boolean> getEditable();

  @NotNull
  GenericDomValue<String> getDescription();
}
