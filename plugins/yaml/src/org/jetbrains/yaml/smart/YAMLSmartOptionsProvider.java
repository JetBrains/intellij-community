// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.smart;

import com.intellij.openapi.options.ConfigurableBuilder;
import org.jetbrains.yaml.YAMLBundle;

public class YAMLSmartOptionsProvider extends ConfigurableBuilder {
  public YAMLSmartOptionsProvider() {
    super(YAMLBundle.message("yaml.smartkeys.option.title"));
    YAMLEditorOptions options = YAMLEditorOptions.getInstance();
    checkBox(YAMLBundle.message("yaml.smartkeys.option.paste"),
             options::isUseSmartPaste,
             options::setUseSmartPaste);
  }
}
