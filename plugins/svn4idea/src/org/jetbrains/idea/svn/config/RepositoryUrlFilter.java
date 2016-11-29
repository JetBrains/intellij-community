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

import org.jetbrains.idea.svn.SvnApplicationSettings;
import org.jetbrains.idea.svn.auth.SvnAuthenticationManager;

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
      if (SvnAuthenticationManager.checkHostGroup(url, patterns, exceptions)) {
        result.add(url);
      }
    }

    Collections.sort(result);

    myListener.onListChanged(result);

    myDefaultFilter.execute(urls);
  }
}
