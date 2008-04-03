package org.jetbrains.idea.svn.config;

import java.util.List;

public interface RepositoryUrlsListener {
  void onListChanged(final List<String> urls);
}
