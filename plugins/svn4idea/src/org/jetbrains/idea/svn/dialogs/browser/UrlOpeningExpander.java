// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.dialogs.browser;

import com.intellij.util.NotNullFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.dialogs.RepositoryBrowserComponent;
import org.jetbrains.idea.svn.dialogs.browserCache.Expander;

import static org.jetbrains.idea.svn.SvnUtil.isAncestor;

public class UrlOpeningExpander extends AbstractOpeningExpander {
  private final @NotNull Url myUrl;

  UrlOpeningExpander(@NotNull RepositoryBrowserComponent browser, @NotNull Url selectionPath, @NotNull Url url) {
    super(browser, selectionPath);
    myUrl = url;
  }

  @Override
  protected ExpandVariants expandNode(@NotNull Url url) {
    if (myUrl.equals(url)) {
      return ExpandVariants.EXPAND_AND_EXIT;
    }
    if (isAncestor(url, myUrl)) {
      return ExpandVariants.EXPAND_CONTINUE;
    }
    return ExpandVariants.DO_NOTHING;
  }

  @Override
  protected boolean checkChild(@NotNull Url childUrl) {
    return isAncestor(childUrl, myUrl);
  }

  public static class Factory implements NotNullFunction<RepositoryBrowserComponent, Expander> {
    private final @NotNull Url myUrl;

    public Factory(@NotNull Url url) {
      myUrl = url;
    }

    @Override
    public @NotNull Expander fun(final RepositoryBrowserComponent repositoryBrowserComponent) {
      return new UrlOpeningExpander(repositoryBrowserComponent, myUrl, myUrl);
    }
  }
}
