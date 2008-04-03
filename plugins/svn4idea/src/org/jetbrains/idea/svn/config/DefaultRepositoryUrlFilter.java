package org.jetbrains.idea.svn.config;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class DefaultRepositoryUrlFilter {
  private final RepositoryUrlsListener myListener;
  private final SvnConfigureProxiesComponent myComponent;

  public DefaultRepositoryUrlFilter(final SvnConfigureProxiesComponent component, final RepositoryUrlsListener listener) {
    myComponent = component;
    myListener = listener;
  }

  public void execute(final Collection<String> urls) {
    final List<String> filtered = myComponent.getGlobalGroupRepositories(urls);
    Collections.sort(filtered);

    myListener.onListChanged(filtered);
  }
}
