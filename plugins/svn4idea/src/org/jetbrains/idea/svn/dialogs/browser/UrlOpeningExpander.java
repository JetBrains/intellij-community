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
import org.tmatesoft.svn.core.SVNURL;

import static org.jetbrains.idea.svn.SvnUtil.isAncestor;

public class UrlOpeningExpander extends AbstractOpeningExpander {
  @NotNull private final SVNURL myUrl;

  UrlOpeningExpander(@NotNull RepositoryBrowserComponent browser, @NotNull SVNURL selectionPath, @NotNull SVNURL url) {
    super(browser, selectionPath);
    myUrl = url;
  }

  @Override
  protected ExpandVariants expandNode(@NotNull SVNURL url) {
    if (myUrl.equals(url)) {
      return ExpandVariants.EXPAND_AND_EXIT;
    }
    if (isAncestor(url, myUrl)) {
      return ExpandVariants.EXPAND_CONTINUE;
    }
    return ExpandVariants.DO_NOTHING;
  }

  @Override
  protected boolean checkChild(@NotNull SVNURL childUrl) {
    return isAncestor(childUrl, myUrl);
  }

  public static class Factory implements NotNullFunction<RepositoryBrowserComponent, Expander> {
    @NotNull private final SVNURL myUrl;

    public Factory(@NotNull SVNURL url) {
      myUrl = url;
    }

    @NotNull
    public Expander fun(final RepositoryBrowserComponent repositoryBrowserComponent) {
      return new UrlOpeningExpander(repositoryBrowserComponent, myUrl, myUrl);
    }
  }
}
