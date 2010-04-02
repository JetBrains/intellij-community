/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

  private final JDOMExternalizableAdapter myAdapter;

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
