package com.intellij.ide.browsers;

import com.google.common.base.CharMatcher;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Tag("browser")
public class StartBrowserSettings {
  private boolean mySelected;
  private WebBrowser myBrowser;

  private String myUrl;
  private boolean myStartJavaScriptDebugger;

  @Attribute("start")
  public boolean isSelected() {
    return mySelected;
  }

  public void setSelected(boolean selected) {
    mySelected = selected;
  }

  @Nullable
  @Attribute(value = "name", converter = WebBrowserReferenceConverter.class)
  public WebBrowser getBrowser() {
    return myBrowser;
  }

  public void setBrowser(@Nullable WebBrowser value) {
    myBrowser = value;
  }

  @Nullable
  @Attribute
  public String getUrl() {
    return myUrl;
  }

  public void setUrl(@Nullable String value) {
    String normalized = StringUtil.nullize(value, true);
    if (normalized != null) {
      normalized = CharMatcher.WHITESPACE.trimFrom(normalized);
    }
    myUrl = normalized;
  }

  @Attribute("with-js-debugger")
  public boolean isStartJavaScriptDebugger() {
    return myStartJavaScriptDebugger;
  }

  public void setStartJavaScriptDebugger(boolean value) {
    myStartJavaScriptDebugger = value;
  }

  @NotNull
  public static StartBrowserSettings readExternal(@NotNull Element parent) {
    Element state = parent.getChild("browser");
    StartBrowserSettings settings = new StartBrowserSettings();
    if (state != null) {
      XmlSerializer.deserializeInto(settings, state);
    }
    return settings;
  }

  public void writeExternal(@NotNull Element parent) {
    Element state = XmlSerializer.serialize(this, new SkipDefaultValuesSerializationFilters());
    if (!JDOMUtil.isEmpty(state)) {
      parent.addContent(state);
    }
  }
}
