/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.openapi.samples;

import com.intellij.openapi.components.ProjectComponent;

/**
 * <h3>SampleProjectPlugin</h3>
 * Project level plugin sample showing IDEA <b>OpenAPI</b> basics.<br>
 * Implements <code>ApplicationComponent</code> interface.
 */
public class SampleProjectPlugin implements ProjectComponent {
  /**
   * Method is called after plugin is already created and configured. Plugin can start to communicate with
   * other plugins only in this method.
   */
  public void initComponent() {
    //System.out.println("SampleProjectPlugin: initComponent");
  }

  /**
   * This method is called on plugin disposal.
   */
  public void disposeComponent() {
    //System.out.println("SampleProjectPlugin: disposeComponent");
  }

  /**
   * Invoked when project is opened.
   */
  public void projectOpened() {
    System.out.println("SampleProjectPlugin: projectOpened");
  }

  /**
   * Invoked when project is closed.
   */
  public void projectClosed() {
    System.out.println("SampleProjectPlugin: projectClosed");
  }

  /**
   * Returns the name of component
   * @return String representing component name. Use plugin_name.component_name notation.
   */
  public String getComponentName() {
    return "Sample.SampleProjectPlugin";
  }
}
