// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
@Service(Service.Level.APP)
@State(name = "HtmlSettings", storages = @Storage("editor.xml"), category = SettingsCategory.CODE)
public final class HtmlSettings implements PersistentStateComponent<HtmlSettings> {

  public boolean AUTO_POPUP_TAG_CODE_COMPLETION_ON_TYPING_IN_TEXT = true;

  public static HtmlSettings getInstance() {
    return ApplicationManager.getApplication().getService(HtmlSettings.class);
  }

  @Override
  public HtmlSettings getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull final HtmlSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
