// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.designer.model;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.JDOMUtil;
import org.jdom.Element;

/**
 * @author Alexander Lobas
 */
public abstract class ModelLoader {
  protected static final Logger LOG = Logger.getInstance(ModelLoader.class);

  protected final Project myProject;

  protected ModelLoader(Project project) {
    myProject = project;
  }

  protected final void load(String name) {
    try {
      loadDocument(JDOMUtil.load(getClass().getResourceAsStream(name)));
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