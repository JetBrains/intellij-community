/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.designer.model;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;

import java.io.InputStream;

/**
 * @author Alexander Lobas
 */
public abstract class ModelLoader {
  protected static final Logger LOG = Logger.getInstance("#com.intellij.designer.model.ModelLoader");

  protected final Project myProject;

  protected ModelLoader(Project project) {
    myProject = project;
  }

  protected final void load(String name) {
    try {
      InputStream stream = getClass().getResourceAsStream(name);
      Document document = new SAXBuilder().build(stream);
      stream.close();

      loadDocument(document.getRootElement());
    }
    catch (Throwable e) {
      LOG.error(e);
    }
  }

  protected abstract void loadDocument(Element rootElement) throws Exception;

  public final Project getProject() {
    return myProject;
  }
}