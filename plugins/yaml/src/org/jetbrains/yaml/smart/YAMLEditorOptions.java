// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.smart;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

@State(
  name = "YamlEditorOptions",
  storages = @Storage("editor.xml")
)
public class YAMLEditorOptions implements PersistentStateComponent<YAMLEditorOptions> {
  private boolean myUseSmartPaste = true;

  @Override
  @NotNull
  public YAMLEditorOptions getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull final YAMLEditorOptions state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public boolean isUseSmartPaste() {
    return myUseSmartPaste;
  }

  public void setUseSmartPaste(boolean useSmartPaste) {
    this.myUseSmartPaste = useSmartPaste;
  }

  @NotNull
  public static YAMLEditorOptions getInstance() {
    return ApplicationManager.getApplication().getService(YAMLEditorOptions.class);
  }
}
