/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
    myProperties = properties;
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

  public void setTimeout(final String value) {
    myProperties.put(SvnServerFileKeys.TIMEOUT, value);
  }

  public String getPatterns() {
    return myPattern;
  }

  public void setPatterns(final String value) {
    myPattern = value;
  }

  protected Map<String, String> createPropertiesCopy() {
    final Map<String, String> copyProperties = new HashMap<>();
    copyProperties.putAll(myProperties);
    return copyProperties;
  }

  public ProxyGroup copy() {
    return new ProxyGroup(myGroupName, myPattern, createPropertiesCopy());
  }
}
