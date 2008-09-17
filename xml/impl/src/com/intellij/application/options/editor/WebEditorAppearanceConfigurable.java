package com.intellij.application.options.editor;

import com.intellij.openapi.options.BeanConfigurable;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.xml.XmlBundle;

/**
 * @author yole
 */
public class WebEditorAppearanceConfigurable extends BeanConfigurable<WebEditorOptions> implements UnnamedConfigurable {
  protected WebEditorAppearanceConfigurable() {
    super(WebEditorOptions.getInstance());
    checkBox("breadcrumbsEnabled", XmlBundle.message("xml.editor.options.breadcrumbs.title"));
    checkBox("showCssColorPreviewInGutter", "Show CSS Color preview icon in gutter");
  }
}
