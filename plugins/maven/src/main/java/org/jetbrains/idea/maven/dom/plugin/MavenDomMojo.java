package org.jetbrains.idea.maven.dom.plugin;

import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenDomElement;

public interface MavenDomMojo extends MavenDomElement {
  @NotNull
  GenericDomValue<String> getGoal();

  @NotNull
  MavenDomParameters getParameters();
}
