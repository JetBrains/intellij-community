package org.jetbrains.idea.maven.dom;

import com.intellij.openapi.module.Module;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.xml.DomFileDescription;
import com.intellij.util.xml.highlighting.DomElementsAnnotator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.annotator.MavenDomAnnotator;

public abstract class MavenDomFileDescription<T> extends DomFileDescription<T> {
  public MavenDomFileDescription(Class<T> rootElementClass, String rootTagName) {
    super(rootElementClass, rootTagName);
  }

  public boolean isMyFile(@NotNull XmlFile file, final Module module) {
    return MavenDomUtil.isPomFile(file) && super.isMyFile(file, module);
  }

  @Override
  public DomElementsAnnotator createAnnotator() {
    return new MavenDomAnnotator();
  }
}
