/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.spellchecker.state;

import com.intellij.openapi.components.StateSplitter;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.text.UniqueNameGenerator;
import org.jdom.Element;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 *
 * @author shkate@jetbrains.com
 */
public class ProjectDictionarySplitter implements StateSplitter {
  @Override
  public List<Pair<Element, String>> splitState(Element e) {
    final UniqueNameGenerator generator = new UniqueNameGenerator();
    List<Pair<Element, String>> result = new ArrayList<Pair<Element, String>>();
    for (Element element : e.getChildren()) {
      result.add(Pair.create(element, generator.generateUniqueName(FileUtil.sanitizeFileName(element.getAttributeValue(DictionaryState.NAME_ATTRIBUTE))) + ".xml"));
    }
    return result;
  }

  @Override
  public void mergeStatesInto(Element target, Element[] elements) {
    for (Element e : elements) {
      target.addContent(e);
    }
  }
}