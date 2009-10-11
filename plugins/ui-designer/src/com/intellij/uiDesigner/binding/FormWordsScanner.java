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

package com.intellij.uiDesigner.binding;

import com.intellij.lang.cacheBuilder.SimpleWordsScanner;
import com.intellij.lang.cacheBuilder.WordOccurrence;
import com.intellij.util.Processor;
import com.intellij.uiDesigner.lw.LwRootContainer;
import com.intellij.uiDesigner.lw.IComponent;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.compiler.AlienFormFileException;
import com.intellij.uiDesigner.compiler.UnexpectedFormElementException;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.openapi.diagnostic.Logger;
import org.jdom.input.JDOMParseException;

/**
 * @author yole
 */
public class FormWordsScanner extends SimpleWordsScanner {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.binding.FormWordsScanner");

  @Override
  public void processWords(CharSequence fileText, final Processor<WordOccurrence> processor) {
    super.processWords(fileText, processor);

    try {
      LwRootContainer container = Utils.getRootContainer(fileText.toString(), null/*no need component classes*/);
      String className = container.getClassToBind();
      if (className != null) {
        processClassAndPackagesNames(className, processor);
      }

      FormEditingUtil.iterate(container,
                              new FormEditingUtil.ComponentVisitor() {
                                WordOccurrence occurence;
                                public boolean visit(IComponent iComponent) {
                                  String componentClassName = iComponent.getComponentClassName();
                                  processClassAndPackagesNames(componentClassName, processor);
                                  final String binding = iComponent.getBinding();
                                  if (binding != null) {
                                    if (occurence == null) occurence = new WordOccurrence(binding, 0, binding.length(),WordOccurrence.Kind.FOREIGN_LANGUAGE);
                                    else occurence.init(binding, 0, binding.length(),WordOccurrence.Kind.FOREIGN_LANGUAGE);
                                    processor.process(occurence);
                                  }
                                  return true;
                                }
                              });
    }
    catch(AlienFormFileException ex) {
      // ignore
    }
    catch(UnexpectedFormElementException ex) {
      // ignore
    }
    catch(JDOMParseException ex) {
      // ignore
    }
    catch (Exception e) {
      LOG.error("Error indexing form file", e);
    }
  }

  private static void processClassAndPackagesNames(String qName, final Processor<WordOccurrence> processor) {
    WordOccurrence occurrence = new WordOccurrence(qName, 0, qName.length(), WordOccurrence.Kind.FOREIGN_LANGUAGE);
    processor.process(occurrence);
    int idx = qName.lastIndexOf('.');
    
    while (idx > 0) {
      qName = qName.substring(0, idx);
      occurrence.init(qName, 0,qName.length(),WordOccurrence.Kind.FOREIGN_LANGUAGE);
      processor.process(occurrence);
      idx = qName.lastIndexOf('.');
    }
  }
}
