package org.jetbrains.idea.maven.dom.annotator;

import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder;
import com.intellij.util.xml.highlighting.DomElementsAnnotator;

public class MavenDomAnnotator implements DomElementsAnnotator {
  public void annotate(DomElement element, DomElementAnnotationHolder holder) {
    //Project project = element.getManager().getProject();
    //MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(project);
    //if (element instanceof MavenDomProjectModel) {
    //  String groupId = ((MavenDomProjectModel)element).getGroupId().getValue();
    //}
  }
}
