package org.jetbrains.idea.svn.config;

import java.util.HashMap;
import java.util.Map;

public class ProxyGroup {
  private String myGroupName;
  private String myPattern;

  // no sence in keeping 'named' referencies to properties - they are just put through into a file;
  // svnkit has its internal named referencies
  private final Map<String, String> myProperties;

  public ProxyGroup(final String groupName, final String patterns, final Map<String, String> properties) {
    myGroupName = groupName;
    myPattern = patterns;
    myProperties = new HashMap<String, String>();
    myProperties.putAll(properties);
  }

  public Map<String, String> getProperties() {
    return myProperties;
  }

  public boolean isDefault() {
    return false;
  }

  public void setName(final String value) {
    myGroupName = value;
  }

  public String getName() {
    return myGroupName;
  }

  public String getPort() {
    return myProperties.get(SvnServerFileKeys.PORT);
  }

  public String getTimeout() {
    return myProperties.get(SvnServerFileKeys.TIMEOUT);
  }

  public String getPatterns() {
    return myPattern;
  }

  public void setPatterns(final String value) {
    myPattern = value;
  }

  protected Map<String, String> createPropertiesCopy() {
    final Map<String, String> copyProperties = new HashMap<String, String>();
    copyProperties.putAll(myProperties);
    return copyProperties;
  }

  public ProxyGroup copy() {
    return new ProxyGroup(myGroupName, myPattern, createPropertiesCopy());
  }
}
