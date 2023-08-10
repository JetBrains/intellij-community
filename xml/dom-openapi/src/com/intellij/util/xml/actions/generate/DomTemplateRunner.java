// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml.actions.generate;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.util.xml.DomElement;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public abstract class DomTemplateRunner {

  public static DomTemplateRunner getInstance(Project project) {
    return project.getService(DomTemplateRunner.class);
  }

  public abstract <T extends DomElement> void  runTemplate(final T t, final String mappingId, final Editor editor);

  public abstract <T extends DomElement> void  runTemplate(final T t, final String mappingId, final Editor editor,@NotNull Map<String, String> predefinedVars);

}
