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
package org.jetbrains.idea.svn.integrate;

import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;

import java.util.Comparator;

/**
* Created by IntelliJ IDEA.
* User: Irina.Chernushina
* Date: 02.07.2010
* Time: 17:26:50
* To change this template use File | Settings | File Templates.
*/
public class ByNumberChangeListComparator implements Comparator<CommittedChangeList> {
  private final static ByNumberChangeListComparator ourInstance = new ByNumberChangeListComparator();

  public static ByNumberChangeListComparator getInstance() {
    return ourInstance;
  }

  public int compare(final CommittedChangeList o1, final CommittedChangeList o2) {
    return (int) (o1.getNumber() - o2.getNumber());
  }
}
