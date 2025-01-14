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
package org.jetbrains.idea.maven.dom.converters;

import com.intellij.util.xml.ConvertContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.MavenDomBundle;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.jetbrains.idea.maven.dom.MavenDomUtil.isAtLeastMaven4;

public class MavenModelVersionConverter extends MavenConstantListConverter {
  private static final String VERSION_4_0 = "4.0.0";
  private static final String VERSION_4_1 = "4.1.0";
  private static final List<String> VALUES_MAVEN_3 = Collections.singletonList(VERSION_4_0);
  private static final List<String> VALUES_MAVEN_4 = Arrays.asList(VERSION_4_0, VERSION_4_1);

  @Override
  protected Collection<String> getValues(@NotNull ConvertContext context) {
    if (isAtLeastMaven4(context.getFile().getVirtualFile(), context.getProject())) {
      return VALUES_MAVEN_4;
    } else {
      return VALUES_MAVEN_3;
    }
  }

  @Override
  public String getErrorMessage(@Nullable String s, @NotNull ConvertContext context) {
    return MavenDomBundle.message("inspection.message.unsupported.model.version.only.version.supported", getValues(context));
  }
}
