// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.config;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnApplicationSettings;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class RepositoryUrlFilter implements PatternsListener {
  private final RepositoryUrlsListener myListener;
  private final SvnConfigureProxiesComponent myComponent;
  private final RepositoryUrlsListener myDefaultGroupListener;

  public RepositoryUrlFilter(@NotNull RepositoryUrlsListener listener,
                             @NotNull SvnConfigureProxiesComponent component,
                             @NotNull RepositoryUrlsListener defaultGroupListener) {
    myListener = listener;
    myComponent = component;
    myDefaultGroupListener = defaultGroupListener;
  }

  @Override
  public void onChange(final String patterns, final String exceptions) {
    final Collection<String> urls = SvnApplicationSettings.getInstance().getCheckoutURLs();
    final List<String> result = new ArrayList<>();

    for (String url : urls) {
      if (SvnIniFile.checkHostGroup(url, patterns, exceptions)) {
        result.add(url);
      }
    }

    Collections.sort(result);
    myListener.onListChanged(result);

    notifyDefaultGroup(urls);
  }

  private void notifyDefaultGroup(@NotNull Collection<String> urls) {
    List<String> filtered = myComponent.getGlobalGroupRepositories(urls);
    Collections.sort(filtered);

    myDefaultGroupListener.onListChanged(filtered);
  }
}
