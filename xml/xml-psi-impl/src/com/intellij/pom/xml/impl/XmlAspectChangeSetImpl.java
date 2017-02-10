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
package com.intellij.pom.xml.impl;

import com.intellij.pom.PomModel;
import com.intellij.pom.PomModelAspect;
import com.intellij.pom.event.PomChangeSet;
import com.intellij.pom.xml.XmlAspect;
import com.intellij.pom.xml.XmlChangeSet;
import com.intellij.pom.xml.events.XmlChange;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class XmlAspectChangeSetImpl implements XmlChangeSet {
  private final PomModel myModel;
  private final List<XmlChange> myChanges = new ArrayList<>();
  private final List<XmlFile> myChangedFiles = new ArrayList<>();

  public XmlAspectChangeSetImpl(PomModel model) {
    myModel = model;
  }

  public XmlAspectChangeSetImpl(final PomModel model, @Nullable final XmlFile xmlFile) {
    this(model);
    if (xmlFile != null) {
      addChangedFile(xmlFile);
    }
  }

  @Override
  public List<XmlChange> getChanges(){
    return Collections.unmodifiableList(myChanges);
  }

  @Override
  @NotNull
  public PomModelAspect getAspect() {
    return myModel.getModelAspect(XmlAspect.class);
  }

  @Override
  public void merge(@NotNull PomChangeSet blocked) {
    final List<XmlChange> changes = ((XmlAspectChangeSetImpl)blocked).myChanges;
    for (XmlChange xmlChange : changes) {
      add(xmlChange);
    }
  }

  @Override
  public void add(XmlChange xmlChange) {
    myChanges.add(xmlChange);
  }

  @Override
  public void clear() {
    myChanges.clear();
  }

  @Override
  @NotNull
  public Iterable<XmlFile> getChangedFiles() {
    return myChangedFiles;
  }

  @Override
  public String toString(){
    final StringBuilder buffer = new StringBuilder();
    final Iterator<XmlChange> iterator = myChanges.iterator();
    while (iterator.hasNext()) {
      XmlChange xmlChange = iterator.next();
      buffer.append("(");
      buffer.append(xmlChange);
      buffer.append(")");
      if(iterator.hasNext()) buffer.append(", ");
    }
    return buffer.toString();
  }

  @Override
  public void addChangedFile(@NotNull final XmlFile xmlFile) {
    myChangedFiles.add(xmlFile);
  }
}
