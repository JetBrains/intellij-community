package com.intellij.ide.browsers;

import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey Simonchik
 */
public class StartBrowserSettings {
  private static final String BROWSER_ELEMENT = "browser";
  private static final String START_BROWSER_ATTR = "start";
  private static final String BROWSER_NAME_ATTR = "name";
  private static final String URL_ATTR = "url";
  private static final String WITH_JS_DEBUGGER_ATTR = "with-js-debugger";

  private final boolean mySelected;
  private final BrowsersConfiguration.BrowserFamily myBrowser;
  private final String myUrl;
  private final boolean myStartJavaScriptDebugger;

  private StartBrowserSettings(boolean selected,
                               @Nullable BrowsersConfiguration.BrowserFamily browser,
                               @NotNull String url,
                               boolean startJavaScriptDebugger) {
    mySelected = selected;
    myBrowser = browser;
    myUrl = url;
    myStartJavaScriptDebugger = startJavaScriptDebugger;
  }

  public boolean isSelected() {
    return mySelected;
  }

  @Nullable
  public BrowsersConfiguration.BrowserFamily getBrowser() {
    return myBrowser;
  }

  public String getUrl() {
    return myUrl;
  }

  public boolean isStartJavaScriptDebugger() {
    return myStartJavaScriptDebugger;
  }

  @NotNull
  public static StartBrowserSettings readExternal(@NotNull Element parent) {
    Builder builder = new Builder();
    Element child = parent.getChild(BROWSER_ELEMENT);
    if (child != null) {
      builder.setSelected(Boolean.parseBoolean(getAttrValue(child, START_BROWSER_ATTR)));
      builder.setBrowser(BrowsersConfiguration.getInstance().findFamilyByName(getAttrValue(child, BROWSER_NAME_ATTR)));
      builder.setUrl(StringUtil.notNullize(getAttrValue(child, URL_ATTR)));
      builder.setStartJavaScriptDebugger(Boolean.parseBoolean(getAttrValue(child, WITH_JS_DEBUGGER_ATTR)));
    }
    return builder.build();
  }

  public void writeExternal(@NotNull Element parent) {
    Element child = new Element(BROWSER_ELEMENT);
    child.setAttribute(START_BROWSER_ATTR, String.valueOf(isSelected()));
    if (myBrowser != null) {
      child.setAttribute(BROWSER_NAME_ATTR, myBrowser.getName());
    }
    child.setAttribute(URL_ATTR, getUrl());
    child.setAttribute(WITH_JS_DEBUGGER_ATTR, String.valueOf(isStartJavaScriptDebugger()));
    parent.addContent(child);
  }

  @Nullable
  private static String getAttrValue(Element element, String attrKey) {
    Attribute attribute = element.getAttribute(attrKey);
    return attribute != null ? attribute.getValue() : null;
  }

  public static class Builder {
    private boolean mySelected;
    private BrowsersConfiguration.BrowserFamily myBrowser;
    private String myUrl = "";
    private boolean myStartJavaScriptDebugger;

    public Builder() {
    }

    public Builder(@Nullable StartBrowserSettings settings) {
      if (settings != null) {
        mySelected = settings.isSelected();
        myBrowser = settings.getBrowser();
        myUrl = settings.getUrl();
        myStartJavaScriptDebugger = settings.isStartJavaScriptDebugger();
      }
    }

    public Builder setSelected(boolean selected) {
      mySelected = selected;
      return this;
    }

    /**
     * @param browser {@link com.intellij.ide.browsers.BrowsersConfiguration.BrowserFamily} instance,
     *                null if Default browser needed
     */
    public Builder setBrowser(@Nullable BrowsersConfiguration.BrowserFamily browser) {
      myBrowser = browser;
      return this;
    }

    public Builder setUrl(@NotNull String url) {
      myUrl = url;
      return this;
    }

    public Builder setStartJavaScriptDebugger(boolean startJavaScriptDebugger) {
      myStartJavaScriptDebugger = startJavaScriptDebugger;
      return this;
    }

    @NotNull
    public StartBrowserSettings build() {
      return new StartBrowserSettings(mySelected, myBrowser, myUrl, myStartJavaScriptDebugger);
    }
  }
}
