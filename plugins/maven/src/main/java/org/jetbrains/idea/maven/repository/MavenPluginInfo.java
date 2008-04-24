package org.jetbrains.idea.maven.repository;

import org.jdom.Element;
import org.jetbrains.idea.maven.core.util.JDOMReader;

import java.io.InputStream;
import java.util.*;

public class MavenPluginInfo {
  private String myGroupId;
  private String myArtifactId;
  private String myVersion;
  private String myGoalPrefix;
  private Map<String, Mojo> myMojos;

  public MavenPluginInfo(InputStream inputStream) {
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
      List<ParameterInfo> params = readParameterInfos(r, mojoElement);

      result.put(goal, new Mojo(goal, params));
    }

    return result;
  }

  private List<ParameterInfo> readParameterInfos(JDOMReader r, Element mojoElement) {
    Element paramsElement = r.getChild(mojoElement, "parameters");
    if (paramsElement == null) return Collections.emptyList();

    List<ParameterInfo> result = new ArrayList<ParameterInfo>();
    for (Element paramElement : r.getChildren(paramsElement, "parameter")) {
      String required = r.getChildText(paramElement, "required");
      if (required.equals("true")) continue;

      String name = r.getChildText(paramElement, "name");
      String desc = r.getChildText(paramElement, "description");
      result.add(new ParameterInfo(name, desc));
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
    private List<ParameterInfo> myParameterInfos;

    private Mojo(String goal, List<ParameterInfo> parameterInfos) {
      myGoal = goal;
      myParameterInfos = parameterInfos;
    }

    public String getGoal() {
      return myGoal;
    }

    public String getQualifiedGoal() {
      return myGoalPrefix + ":" + myGoal;
    }

    public List<ParameterInfo> getParameterInfos() {
      return myParameterInfos;
    }

    public ParameterInfo findParameterInfo(String name) {
      for (ParameterInfo i : myParameterInfos) {
        if (i.getName().equals(name)) return i;
      }
      return null;
    }
  }

  public static class ParameterInfo {
    private String myName;
    private String myDescription;

    public ParameterInfo(String name, String description) {
      myName = name;
      myDescription = description;
    }

    public String getDescription() {
      return myDescription;
    }

    public String getName() {
      return myName;
    }
  }
}
