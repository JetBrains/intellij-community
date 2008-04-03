package org.jetbrains.idea.svn.config;

import org.jetbrains.idea.svn.SvnApplicationSettings;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNOptions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class RepositoryUrlFilter implements PatternsListener {
  private final RepositoryUrlsListener myListener;
  private final DefaultRepositoryUrlFilter myDefaultFilter;

  public RepositoryUrlFilter(final RepositoryUrlsListener listener, final SvnConfigureProxiesComponent component,
                             final RepositoryUrlsListener defaultGroupListener) {
    myListener = listener;
    myDefaultFilter = new DefaultRepositoryUrlFilter(component, defaultGroupListener);
  }

  public void onChange(final String patterns, final String exceptions) {
    final Collection<String> urls = SvnApplicationSettings.getInstance().getCheckoutURLs();
    final List<String> result = new ArrayList<String>();

    for (String url : urls) {
      if ((! DefaultSVNOptions.matches(exceptions, url)) && (DefaultSVNOptions.matches(patterns, url))) {
        result.add(url);
      }
    }

    Collections.sort(result);

    myListener.onListChanged(result);

    myDefaultFilter.execute(urls);
  }
}
