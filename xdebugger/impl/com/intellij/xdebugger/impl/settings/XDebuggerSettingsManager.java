package com.intellij.xdebugger.impl.settings;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.xdebugger.impl.XDebuggerUtilImpl;
import com.intellij.xdebugger.settings.XDebuggerSettings;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author nik
 */
@State(
    name = XDebuggerSettingsManager.COMPONENT_NAME,
    storages = {
      @Storage(
          id ="other",
          file = "$APP_CONFIG$/other.xml"
      )
    }
)
public class XDebuggerSettingsManager implements ApplicationComponent, PersistentStateComponent<XDebuggerSettingsManager.SettingsState>{
  @NonNls public static final String COMPONENT_NAME = "XDebuggerSettings";
  private Map<String, XDebuggerSettings<?>> mySettingsById;

  public static XDebuggerSettingsManager getInstance() {
    return ApplicationManager.getApplication().getComponent(XDebuggerSettingsManager.class);
  }

  public SettingsState getState() {
    SettingsState settingsState = new SettingsState();
    for (XDebuggerSettings<?> settings : getSettingsList()) {
      SpecificSettingsState state = new SpecificSettingsState();
      state.setId(settings.getId());
      state.setSettingsElement(XmlSerializer.serialize(settings.getState(), new SkipDefaultValuesSerializationFilters()));
      settingsState.getSpecificStates().add(state);
    }
    return settingsState;
  }

  private Collection<XDebuggerSettings<?>> getSettingsList() {
    initSettings();
    return mySettingsById.values();
  }

  public void loadState(final SettingsState state) {
    for (SpecificSettingsState settingsState : state.getSpecificStates()) {
      XDebuggerSettings<?> settings = findSettings(settingsState.getId());
      if (settings != null) {
        loadState(settings, settingsState.getSettingsElement());
      }
    }
  }

  private static <T> void loadState(final XDebuggerSettings<T> settings, final Element settingsElement) {
    Class stateClass = XDebuggerUtilImpl.getStateClass(settings.getClass());
    //noinspection unchecked
    settings.loadState((T)XmlSerializer.deserialize(settingsElement, stateClass));
  }


  private XDebuggerSettings findSettings(String id) {
    initSettings();
    return mySettingsById.get(id);
  }

  private void initSettings() {
    if (mySettingsById == null) {
      mySettingsById = new HashMap<String, XDebuggerSettings<?>>();
      for (XDebuggerSettings<?> settings : Extensions.getExtensions(XDebuggerSettings.EXTENSION_POINT)) {
        mySettingsById.put(settings.getId(), settings);
      }
    }
  }

  @NotNull
  public String getComponentName() {
    return COMPONENT_NAME;
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public static class SettingsState {
    private List<SpecificSettingsState> mySpecificStates = new ArrayList<SpecificSettingsState>();

    @Tag("debuggers")
    @AbstractCollection(surroundWithTag = false)
    public List<SpecificSettingsState> getSpecificStates() {
      return mySpecificStates;
    }

    public void setSpecificStates(final List<SpecificSettingsState> specificStates) {
      mySpecificStates = specificStates;
    }
  }

  @Tag("debugger")
  public static class SpecificSettingsState {
    private String myId;
    private Element mySettingsElement;


    @Attribute("id")
    public String getId() {
      return myId;
    }

    @Tag("configuration")
    public Element getSettingsElement() {
      return mySettingsElement;
    }

    public void setSettingsElement(final Element settingsElement) {
      mySettingsElement = settingsElement;
    }

    public void setId(final String id) {
      myId = id;
    }


  }
}
