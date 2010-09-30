/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.dom.converters;

import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.DomElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.project.MavenProject;

import java.util.Collection;
import java.util.Collections;

public abstract class MavenProjectConstantListConverter extends MavenConstantListConverter {
  protected MavenProjectConstantListConverter() {
  }

  protected MavenProjectConstantListConverter(boolean strict) {
    super(strict);
  }

  protected Collection<String> getValues(@NotNull ConvertContext context) {
    DomElement element = context.getInvocationElement();

    MavenProject project = MavenDomUtil.findContainingProject(element);
    if (project == null) return Collections.emptyList();

    return getValues(context, project);
  }

  protected abstract Collection<String> getValues(@NotNull ConvertContext context, @NotNull MavenProject project);
}