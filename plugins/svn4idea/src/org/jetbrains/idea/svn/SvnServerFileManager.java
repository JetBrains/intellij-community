package org.jetbrains.idea.svn;

import org.jetbrains.idea.svn.config.DefaultProxyGroup;
import org.jetbrains.idea.svn.config.ProxyGroup;

import java.util.Map;
import java.util.Collection;

public interface SvnServerFileManager {
  DefaultProxyGroup getDefaultGroup();
  Map<String, ProxyGroup> getGroups();

  void updateUserServerFile(final Collection<ProxyGroup> newUserGroups);
}
