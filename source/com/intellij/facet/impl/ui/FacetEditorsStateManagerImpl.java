package com.intellij.facet.impl.ui;

import com.intellij.facet.FacetType;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.annotations.MapAnnotation;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.Property;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * @author nik
 */
@State(
    name = "FacetEditorsStateManager",
    storages = {
        @Storage(
            id = "other",
            file = "$WORKSPACE_FILE$"
        )
    }
)
public class FacetEditorsStateManagerImpl extends FacetEditorsStateManager implements PersistentStateComponent<FacetEditorsStateManagerImpl.FacetEditorsStateBean>{
  private Map<String, Object> myFacetTypeStates = new HashMap<String, Object>();
  private FacetEditorsStateBean myBean = new FacetEditorsStateBean();

  public <T> void saveState(@NotNull final FacetType<?, ?> type, final T state) {
    String id = type.getStringId();
    if (state != null) {
      myFacetTypeStates.put(id, state);
    }
    else {
      myFacetTypeStates.remove(id);
      myBean.getFacetTypeElements().remove(id);
    }
  }

  @Nullable
  public <T> T getState(@NotNull final FacetType<?, ?> type, @NotNull final Class<T> aClass) {
    String id = type.getStringId();
    //noinspection unchecked
    T state = (T)myFacetTypeStates.get(id);
    if (state == null) {
      FacetTypeStateBean bean = myBean.getFacetTypeElements().get(id);
      if (bean != null) {
        Element element = bean.getState();
        if (element != null) {
          state = XmlSerializer.deserialize(element, aClass);
        }
      }
    }
    return state;
  }

  public FacetEditorsStateBean getState() {
    for (Map.Entry<String, Object> entry : myFacetTypeStates.entrySet()) {
      FacetTypeStateBean bean = new FacetTypeStateBean();
      bean.setState(XmlSerializer.serialize(entry.getValue()));
      myBean.getFacetTypeElements().put(entry.getKey(), bean);
    }
    return myBean;
  }

  public void loadState(final FacetEditorsStateBean state) {
    myBean = state;
  }

  public static class FacetEditorsStateBean {
    private Map<String, FacetTypeStateBean> myFacetTypeElements = new HashMap<String, FacetTypeStateBean>();

    @Property(surroundWithTag = false)
    @MapAnnotation(surroundKeyWithTag = false, surroundWithTag = false, surroundValueWithTag = false, entryTagName = "facet-type-state", keyAttributeName = "type")
    public Map<String, FacetTypeStateBean> getFacetTypeElements() {
      return myFacetTypeElements;
    }

    public void setFacetTypeElements(final Map<String, FacetTypeStateBean> elements) {
      myFacetTypeElements = elements;
    }
  }

  @Tag("facet-type")
  public static class FacetTypeStateBean {
    private Element myState;

    @Tag("state")
    public Element getState() {
      return myState;
    }

    public void setState(final Element state) {
      myState = state;
    }
  }
}
