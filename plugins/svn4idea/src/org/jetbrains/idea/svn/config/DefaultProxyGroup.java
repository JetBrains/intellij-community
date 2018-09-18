// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.config;

import org.jetbrains.idea.svn.IdeaSVNConfigFile;

import java.util.Map;

public class DefaultProxyGroup extends ProxyGroup {

  public DefaultProxyGroup(final Map<String, String> properties) {
    super(IdeaSVNConfigFile.DEFAULT_GROUP_NAME, "", properties);
  }

  @Override
  public boolean isDefault() {
    return true;
  }

  @Override
  public ProxyGroup copy() {
    return new DefaultProxyGroup(createPropertiesCopy());
  }
}
