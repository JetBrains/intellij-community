package org.jetbrains.idea.maven.repository;

import org.jdom.Element;
import org.jetbrains.idea.maven.core.util.JDOMReader;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

public class PluginPluginInfo {
  private String myGroupId;
  private String myArtifactId;
  private String myVersion;
  private String myGoalPrefix;
  private List<String> myGoals;

  public PluginPluginInfo(InputStream inputStream) {
    JDOMReader r = new JDOMReader(inputStream);

    myGroupId = r.getChildText(r.getRootElement(), "groupId");
    myArtifactId = r.getChildText(r.getRootElement(), "artifactId");
    myVersion = r.getChildText(r.getRootElement(), "version");

    myGoalPrefix = r.getChildText(r.getRootElement(), "goalPrefix");

    myGoals = readGoals(r);
  }

  private List<String> readGoals(JDOMReader r) {
    Element mojos = r.getChild(r.getRootElement(), "mojos");
    if (mojos == null) return Collections.emptyList();

    List<String> result = new ArrayList<String>();
    for (Element mojo : r.getChildren(mojos, "mojo")) {
      result.add(myGoalPrefix + ":" + r.getChildText(mojo, "goal"));
    }
    return result;
  }

  public String getGroupId() {
    return myGroupId;
  }

  public String getArtifactId() {
    return myArtifactId;
  }

  public String getVersion() {
    return myVersion;
  }

  public String getGoalPrefix() {
    return myGoalPrefix;
  }

  public List<String> getGoals() {
    return myGoals;
  }
}
