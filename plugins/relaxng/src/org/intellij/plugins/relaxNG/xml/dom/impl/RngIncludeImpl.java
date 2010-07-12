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

package org.intellij.plugins.relaxNG.xml.dom.impl;

import org.intellij.plugins.relaxNG.xml.dom.RngDefine;
import org.intellij.plugins.relaxNG.xml.dom.RngInclude;

import com.intellij.psi.PsiFile;
import com.intellij.util.xml.DomUtil;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: sweinreuter
 * Date: 19.08.2007
 */
public abstract class RngIncludeImpl extends RngDomElementBase implements RngInclude {
  @Override
  public void accept(Visitor visitor) {
    visitor.visitInclude(this);
  }

  public PsiFile getInclude() {
    return getIncludedFile().getValue();
  }

  @NotNull
  public RngDefine[] getOverrides() {
    // TODO: include stuff inside DIVs - fix when this is actually used
    final List<RngDefine> defines = DomUtil.getChildrenOfType(this, RngDefine.class);
    return defines.toArray(new RngDefine[defines.size()]);
  }
}