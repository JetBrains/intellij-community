// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.config;

import com.intellij.openapi.util.NlsSafe;

import java.util.HashMap;
import java.util.Map;

public class ProxyGroup {
  private String myGroupName;
  private String myPattern;

  // no sence in keeping 'named' referencies to properties - they are just put through into a file;
  // svnkit has its internal named referencies
  private final Map<String, String> myProperties;

  public ProxyGroup(@NlsSafe String groupName, final String patterns, final Map<String, String> properties) {
    myGroupName = groupName;
    myPattern = patterns;
    myProperties = properties;
  }

  public Map<String, String> getProperties() {
    return myProperties;
  }

  public boolean isDefault() {
    return false;
  }

  public void setName(@NlsSafe String value) {
    myGroupName = value;
  }

  public @NlsSafe String getName() {
    return myGroupName;
  }

  public String getPort() {
    return myProperties.get(ServersFileKeys.PORT);
  }

  public String getTimeout() {
    return myProperties.get(ServersFileKeys.TIMEOUT);
  }

  public void setTimeout(final String value) {
    myProperties.put(ServersFileKeys.TIMEOUT, value);
  }

  public String getPatterns() {
    return myPattern;
  }

  public void setPatterns(final String value) {
    myPattern = value;
  }

  protected Map<String, String> createPropertiesCopy() {
    return new HashMap<>(myProperties);
  }

  public ProxyGroup copy() {
    return new ProxyGroup(myGroupName, myPattern, createPropertiesCopy());
  }
}
