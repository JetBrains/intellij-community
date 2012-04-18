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
package org.jetbrains.idea.svn.dialogs.browser;

import com.intellij.util.NotNullFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.dialogs.RepositoryBrowserComponent;
import org.jetbrains.idea.svn.dialogs.browserCache.Expander;

public class UrlOpeningExpander extends AbstractOpeningExpander {
  private final String myUrl;

  UrlOpeningExpander(@NotNull final RepositoryBrowserComponent browser, final String selectionPath, @NotNull final String url) {
    super(browser, selectionPath);
    myUrl = (url.endsWith("/")) ? url.substring(0, url.length() - 1) : url;
  }

  protected ExpandVariants expandNode(final String url) {
    if (myUrl.equals(url)) {
      return ExpandVariants.EXPAND_AND_EXIT;
    }
    if (myUrl.startsWith(url.endsWith("/") ? url : (url + '/'))) {
/*      if ((myUrl.length() == url.length()) || ((myUrl.length() - 1) == url.length())) {
        // do not expand myUrl - the last node just must be visible
        return ExpandVariants.DO_NOTHING;
      }*/
      return ExpandVariants.EXPAND_CONTINUE;
    }
    return ExpandVariants.DO_NOTHING;
  }

  protected boolean checkChild(final String childUrl) {
    return myUrl.startsWith(childUrl);
  }

  public static class Factory implements NotNullFunction<RepositoryBrowserComponent, Expander> {
    private final String myUrl;
    private final String mySelectionUrl;

    public Factory(final String url, final String selectionUrl) {
      myUrl = url;
      mySelectionUrl = selectionUrl;
    }

    @NotNull
    public Expander fun(final RepositoryBrowserComponent repositoryBrowserComponent) {
      return new UrlOpeningExpander(repositoryBrowserComponent, mySelectionUrl, myUrl);
    }
  }
}
