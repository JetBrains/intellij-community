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

package org.intellij.plugins.relaxNG;

import org.intellij.plugins.relaxNG.model.resolve.RelaxSymbolIndex;

import com.intellij.navigation.ChooseByNameContributor;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.project.Project;

import java.util.Collection;

/*
* Created by IntelliJ IDEA.
* User: sweinreuter
* Date: 24.10.2007
*/
public class GotoSymbolContributor implements ChooseByNameContributor {

  public NavigationItem[] getItemsByName(String name, String pattern, Project project, boolean includeNonProjectItems) {
    return RelaxSymbolIndex.getSymbolsByName(name, project, includeNonProjectItems);
  }

  public String[] getNames(Project project, boolean includeNonProjectItems) {
    final Collection<String> names = RelaxSymbolIndex.getSymbolNames(project);
    return names.toArray(new String[names.size()]);
  }
}