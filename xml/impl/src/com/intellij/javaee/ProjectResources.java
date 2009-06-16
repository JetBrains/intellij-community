package com.intellij.javaee;

import com.intellij.application.options.PathMacrosImpl;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.util.JDOMExternalizableAdapter;
import org.jdom.Element;

import java.util.Map;
import java.util.Collections;

/**
* @author Dmitry Avdeev
*/
@State(name = "ProjectResources", storages = {@Storage(id = "default", file = "$PROJECT_FILE$")})
public class ProjectResources extends ExternalResourceManagerImpl implements PersistentStateComponent<Element> {

  private JDOMExternalizableAdapter myAdapter;

  public ProjectResources(PathMacrosImpl pathMacros) {
    super(pathMacros);
    myAdapter = new JDOMExternalizableAdapter(this, "ProjectResources");
  }

  @Override
  protected Map<String, Map<String, String>> computeStdResources() {
    return Collections.emptyMap();   
  }

  public Element getState() {
    return myAdapter.getState();
  }

  public void loadState(Element state) {
    myAdapter.loadState(state);
  }
}
