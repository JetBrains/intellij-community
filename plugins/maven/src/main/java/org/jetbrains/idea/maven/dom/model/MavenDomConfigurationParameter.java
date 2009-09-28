package org.jetbrains.idea.maven.dom.model;

import com.intellij.util.xml.CustomChildren;
import org.jetbrains.idea.maven.dom.MavenDomElement;

import java.util.List;

public interface MavenDomConfigurationParameter extends MavenDomElement {
  @CustomChildren
  List<MavenDomConfigurationParameter> getChildren();
}
