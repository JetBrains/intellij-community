// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.config;

import org.jetbrains.idea.svn.IdeaSVNConfigFile;
import org.jetbrains.idea.svn.SvnApplicationSettings;

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
    final List<String> result = new ArrayList<>();

    for (String url : urls) {
      if (IdeaSVNConfigFile.checkHostGroup(url, patterns, exceptions)) {
        result.add(url);
      }
    }

    Collections.sort(result);

    myListener.onListChanged(result);

    myDefaultFilter.execute(urls);
  }
}
