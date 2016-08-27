/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.dom.intentions;

import com.intellij.codeInsight.daemon.QuickFixActionRegistrar;
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;

/**
 * @author Sang-Jin Park
 */
public class GroovyResolveReferenceQuickFixProvider extends UnresolvedReferenceQuickFixProvider<GrReferenceElement> {

  public void registerFixes(@NotNull GrReferenceElement ref, @NotNull QuickFixActionRegistrar registrar) {
    registrar.register(new GroovyAddMavenDependencyQuickFix(ref));
  }

  @NotNull
  public Class<GrReferenceElement> getReferenceClass() {
    return GrReferenceElement.class;
  }
}
