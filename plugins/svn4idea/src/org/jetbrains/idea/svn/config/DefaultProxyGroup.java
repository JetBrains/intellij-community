package org.jetbrains.idea.svn.config;

import java.util.Map;

public class DefaultProxyGroup extends ProxyGroup {
  public static final String DEFAULT_GROUP_NAME = "global";

  public DefaultProxyGroup(final Map<String, String> properties) {
    super(DEFAULT_GROUP_NAME, "", properties);
  }

  public boolean isDefault() {
    return true;
  }

  public ProxyGroup copy() {
    return new DefaultProxyGroup(createPropertiesCopy());
  }
}
