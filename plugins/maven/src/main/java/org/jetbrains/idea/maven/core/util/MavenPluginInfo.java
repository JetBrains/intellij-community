package org.jetbrains.idea.maven.core.util;

import org.jdom.Element;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class MavenPluginInfo {
  private String myGroupId;
  private String myArtifactId;
  private String myVersion;
  private String myGoalPrefix;
  private Map<String, Mojo> myMojos;

  public MavenPluginInfo(InputStream inputStream) throws IOException {
    JDOMReader r = new JDOMReader(inputStream);

    myGroupId = r.getChildText(r.getRootElement(), "groupId");
    myArtifactId = r.getChildText(r.getRootElement(), "artifactId");
    myVersion = r.getChildText(r.getRootElement(), "version");

    myGoalPrefix = r.getChildText(r.getRootElement(), "goalPrefix");

    myMojos = readMojos(r);
  }

  private Map<String, Mojo> readMojos(JDOMReader r) {
    Element mojosElement = r.getChild(r.getRootElement(), "mojos");
    if (mojosElement == null) return Collections.emptyMap();

    Map<String, Mojo> result = new LinkedHashMap<String, Mojo>();

    for (Element mojoElement : r.getChildren(mojosElement, "mojo")) {
      String goal = r.getChildText(mojoElement, "goal");
      result.put(goal, new Mojo(goal));
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

  public Collection<Mojo> getMojos() {
    return myMojos.values();
  }

  public Mojo findMojo(String name) {
    return myMojos.get(name);
  }

  public class Mojo {
    private String myGoal;

    private Mojo(String goal) {
      myGoal = goal;
    }

    public String getGoal() {
      return myGoal;
    }

    public String getQualifiedGoal() {
      return myGoalPrefix + ":" + myGoal;
    }
  }
}
