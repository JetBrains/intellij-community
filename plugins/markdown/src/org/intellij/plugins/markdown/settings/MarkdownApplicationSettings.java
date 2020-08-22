// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.settings;

import com.intellij.ide.ui.LafManagerListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.messages.Topic;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

@State(
  name = "MarkdownApplicationSettings",
  storages = @Storage("markdown.xml")
)
public final class MarkdownApplicationSettings implements PersistentStateComponent<MarkdownApplicationSettings.State>,
                                                          MarkdownCssSettings.Holder,
                                                          MarkdownPreviewSettings.Holder {

  private State myState = new State();

  public MarkdownApplicationSettings() {
    MarkdownLAFListener lafListener = new MarkdownLAFListener();
    ApplicationManager.getApplication().getMessageBus().connect().subscribe(LafManagerListener.TOPIC, lafListener);
    // Let's init proper CSS scheme
    ApplicationManager.getApplication().invokeLater(() -> {
      MarkdownLAFListener.reinit();
    });
  }

  @NotNull
  public static MarkdownApplicationSettings getInstance() {
    return ServiceManager.getService(MarkdownApplicationSettings.class);
  }

  @Nullable
  @Override
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull State state) {
    myState = state;
  }

  @Override
  public void setMarkdownCssSettings(@NotNull MarkdownCssSettings settings) {
    ApplicationManager.getApplication().getMessageBus().syncPublisher(SettingsChangedListener.TOPIC).beforeSettingsChanged(this);
    myState.myCssSettings = settings;
  }

  @NotNull
  @Override
  public MarkdownCssSettings getMarkdownCssSettings() {
    if (MarkdownCssSettings.DEFAULT.getCustomStylesheetPath().equals(myState.myCssSettings.getCustomStylesheetPath())) {
      return new MarkdownCssSettings(false,
                                     "",
                                     myState.myCssSettings.isTextEnabled(),
                                     myState.myCssSettings.getCustomStylesheetText());
    }

    return myState.myCssSettings;
  }

  @Override
  public void setMarkdownPreviewSettings(@NotNull MarkdownPreviewSettings settings) {
    ApplicationManager.getApplication().getMessageBus().syncPublisher(SettingsChangedListener.TOPIC).beforeSettingsChanged(this);
    myState.myPreviewSettings = settings;
  }

  @NotNull
  @Override
  public MarkdownPreviewSettings getMarkdownPreviewSettings() {
    return myState.myPreviewSettings;
  }

  public void setDisableInjections(boolean disableInjections) {
    myState.myDisableInjections = disableInjections;
  }

  public boolean isDisableInjections() {
    return myState.myDisableInjections;
  }

  public void setHideErrors(boolean hideErrors) {
    myState.myHideErrors = hideErrors;
  }

  public boolean isHideErrors() {
    return myState.myHideErrors;
  }

  public boolean isExtensionsEnabled(String extensionId) {
    Boolean value = myState.myEnabledExtensions.get(extensionId);
    return value != null ? value : false;
  }

  @NotNull
  public Map<String, Boolean> getExtensionsEnabledState() {
    return myState.myEnabledExtensions;
  }

  public void setExtensionsEnabledState(@NotNull Map<String, Boolean> state) {
    myState.myEnabledExtensions = state;
  }

  public static final class State {
    @Property(surroundWithTag = false)
    @NotNull
    private MarkdownCssSettings myCssSettings = MarkdownCssSettings.DEFAULT;

    @Property(surroundWithTag = false)
    @NotNull
    private MarkdownPreviewSettings myPreviewSettings = MarkdownPreviewSettings.DEFAULT;

    @Attribute("DisableInjections")
    private boolean myDisableInjections = false;

    @Attribute("HideErrors")
    private boolean myHideErrors = false;

    @NotNull
    @XMap
    @Tag("enabledExtensions")
    private Map<String, Boolean> myEnabledExtensions = new HashMap<>();
  }

  public interface SettingsChangedListener {
    Topic<SettingsChangedListener> TOPIC = Topic.create("MarkdownApplicationSettingsChanged", SettingsChangedListener.class);

    default void beforeSettingsChanged(@NotNull MarkdownApplicationSettings settings) { }

    default void settingsChanged(@NotNull MarkdownApplicationSettings settings) { }
  }
}
