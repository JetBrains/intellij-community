package org.jetbrains.idea.maven.dom;

import com.intellij.util.xml.DomFileDescription;
import org.jetbrains.idea.maven.dom.plugin.MavenDomPluginModel;

public class MavenDomPluginModelDescription extends DomFileDescription<MavenDomPluginModel> {
  public MavenDomPluginModelDescription() {
    super(MavenDomPluginModel.class, "plugin");
  }
}
