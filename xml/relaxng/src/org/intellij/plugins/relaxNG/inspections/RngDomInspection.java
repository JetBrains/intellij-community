/*
 * Copyright 2007 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.intellij.plugins.relaxNG.inspections;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.util.xml.highlighting.BasicDomElementsInspection;
import org.intellij.plugins.relaxNG.xml.dom.RngDomElement;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: sweinreuter
 * Date: 03.10.2007
 */
public class RngDomInspection extends BasicDomElementsInspection<RngDomElement> {
  public RngDomInspection() {
    super(RngDomElement.class);
  }

  @NotNull
  @Override
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.ERROR;
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Nls
  @NotNull
  public String getGroupDisplayName() {
    return BaseInspection.getRngGroupDisplayName();
  }

  @Nls
  @NotNull
  public String getDisplayName() {
    return "Unresolved References";
  }

  @NonNls
  @NotNull
  public String getShortName() {
    return "UnresolvedReference";
  }
}
