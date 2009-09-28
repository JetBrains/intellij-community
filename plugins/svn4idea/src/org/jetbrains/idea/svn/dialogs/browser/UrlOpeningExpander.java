package org.jetbrains.idea.svn.dialogs.browser;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.dialogs.RepositoryBrowserComponent;
import org.jetbrains.idea.svn.dialogs.browserCache.Expander;
import com.intellij.util.NotNullFunction;

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
