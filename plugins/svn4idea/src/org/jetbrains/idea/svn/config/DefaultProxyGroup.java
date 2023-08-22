// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.config;

import org.jetbrains.annotations.Nls;
import org.jetbrains.idea.svn.SvnBundle;

import java.util.Map;

import static org.jetbrains.idea.svn.config.ServersFileKeys.GLOBAL_SERVER_GROUP;

public class DefaultProxyGroup extends ProxyGroup {

  public DefaultProxyGroup(final Map<String, String> properties) {
    super(GLOBAL_SERVER_GROUP, "", properties);
  }

  @Override
  public @Nls String getDisplayName() {
    return GLOBAL_SERVER_GROUP.equals(getName()) ? SvnBundle.message("global.group") : getName();
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
