package org.intellij.plugins.markdown.settings;

import com.intellij.openapi.fileEditor.TextEditorWithPreview;
import com.intellij.ui.jcef.JBCefApp;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;
import org.intellij.plugins.markdown.ui.preview.MarkdownHtmlPanelProvider;
import org.intellij.plugins.markdown.ui.preview.jcef.JCEFHtmlPanelProvider;
import org.jetbrains.annotations.NotNull;

@Deprecated
public final class MarkdownPreviewSettings {
  public static final MarkdownPreviewSettings DEFAULT = new MarkdownPreviewSettings();

  @Attribute("DefaultSplitLayout")
  @NotNull
  private TextEditorWithPreview.Layout mySplitEditorLayout = TextEditorWithPreview.Layout.SHOW_EDITOR_AND_PREVIEW;

  @Tag("HtmlPanelProviderInfo")
  @Property(surroundWithTag = false)
  @NotNull
  private MarkdownHtmlPanelProvider.ProviderInfo myHtmlPanelProviderInfo =
    JBCefApp.isSupported() ? new JCEFHtmlPanelProvider().getProviderInfo() : new MarkdownHtmlPanelProvider.ProviderInfo("Unavailable", "Unavailable");

  @Attribute("AutoScrollPreview")
  private boolean myIsAutoScrollPreview = true;

  @Attribute("VerticalSplit")
  private boolean myIsVerticalSplit = true;

  public MarkdownPreviewSettings() {
  }

  public MarkdownPreviewSettings(@NotNull TextEditorWithPreview.Layout splitEditorLayout,
                                 @NotNull MarkdownHtmlPanelProvider.ProviderInfo htmlPanelProviderInfo,
                                 boolean isAutoScrollPreview,
                                 boolean isVerticalSplit) {
    mySplitEditorLayout = splitEditorLayout;
    myHtmlPanelProviderInfo = htmlPanelProviderInfo;
    myIsAutoScrollPreview = isAutoScrollPreview;
    myIsVerticalSplit = isVerticalSplit;
  }

  @NotNull
  public TextEditorWithPreview.Layout getSplitEditorLayout() {
    //noinspection ConstantConditions
    if (mySplitEditorLayout == null) {
      mySplitEditorLayout = TextEditorWithPreview.Layout.SHOW_EDITOR_AND_PREVIEW;
    }
    return mySplitEditorLayout;
  }

  @NotNull
  public MarkdownHtmlPanelProvider.ProviderInfo getHtmlPanelProviderInfo() {
    return myHtmlPanelProviderInfo;
  }

  public boolean isAutoScrollPreview() {
    return myIsAutoScrollPreview;
  }

  public boolean isVerticalSplit() {
    return myIsVerticalSplit;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    MarkdownPreviewSettings settings = (MarkdownPreviewSettings)o;

    if (myIsAutoScrollPreview != settings.myIsAutoScrollPreview) return false;
    if (myIsVerticalSplit != settings.myIsVerticalSplit) return false;
    if (mySplitEditorLayout != settings.mySplitEditorLayout) return false;
    if (!myHtmlPanelProviderInfo.equals(settings.myHtmlPanelProviderInfo)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = mySplitEditorLayout.hashCode();
    result = 31 * result + myHtmlPanelProviderInfo.hashCode();
    result = 31 * result + (myIsAutoScrollPreview ? 1 : 0);
    result = 31 * result + (myIsVerticalSplit ? 1 : 0);
    return result;
  }

  public interface Holder {
    void setMarkdownPreviewSettings(@NotNull MarkdownPreviewSettings settings);

    @NotNull
    MarkdownPreviewSettings getMarkdownPreviewSettings();
  }
}
