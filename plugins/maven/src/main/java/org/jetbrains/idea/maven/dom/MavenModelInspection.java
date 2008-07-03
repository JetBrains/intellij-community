package org.jetbrains.idea.maven.dom;

import com.intellij.codeInspection.InspectionToolProvider;
import com.intellij.util.xml.highlighting.BasicDomElementsInspection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.model.MavenModel;

public class MavenModelInspection extends BasicDomElementsInspection<MavenModel> implements InspectionToolProvider {
  public MavenModelInspection() {
    super(MavenModel.class);
  }

  @NotNull
  public String getGroupDisplayName() {
    return MavenDomBundle.message("inspection.group");
  }

  @NotNull
  public String getDisplayName() {
    return MavenDomBundle.message("inspection.name");
  }

  @NotNull
  public String getShortName() {
    return "MavenModelInspection";
  }

  public Class[] getInspectionClasses() {
    return new Class[] {MavenModelInspection.class};
  }
}
