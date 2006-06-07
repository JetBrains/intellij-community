/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.pom.xml;

import com.intellij.pom.PomModelAspect;
import com.intellij.pom.event.PomChangeSet;
import com.intellij.pom.xml.events.XmlChange;
import com.intellij.psi.xml.XmlFile;

import java.util.List;

public interface XmlChangeSet extends PomChangeSet {
  List<XmlChange> getChanges();

  PomModelAspect getAspect();

  void add(XmlChange xmlChange);

  void clear();

  XmlFile getChangedFile();
}
