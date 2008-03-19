package org.jetbrains.idea.maven.core;

import com.intellij.openapi.module.Module;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.xml.DomFileDescription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.Model;
import org.jetbrains.idea.maven.project.Constants;

public class MavenDomDescription extends DomFileDescription<Model>  {
    public MavenDomDescription() {
      super(Model.class, "project");
    }

  public boolean isMyFile(@NotNull XmlFile file, final Module module) {
      String name = file.getName();
      return name.equals(Constants.POM_XML) && super.isMyFile(file, module);
    }
}
