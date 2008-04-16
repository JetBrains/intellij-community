package org.jetbrains.idea.maven.dom;

import com.intellij.codeInspection.InspectionToolProvider;
import com.intellij.util.xml.highlighting.BasicDomElementsInspection;
import org.jetbrains.idea.maven.dom.beans.MavenModel;
import org.jetbrains.annotations.NotNull;

public class MavenModelInspection extends BasicDomElementsInspection<MavenModel> implements InspectionToolProvider {
  public MavenModelInspection() {
    super(MavenModel.class);
  }

  @NotNull
  public String getGroupDisplayName() {
    return "Maven";
  }

  @NotNull
  public String getDisplayName() {
    return "Maven Model Inspection";
  }

  @NotNull
  public String getShortName() {
    return "MavenModelInspection";
  }

  public Class[] getInspectionClasses() {
    return new Class[] {MavenModelInspection.class};
  }
}
