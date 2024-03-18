// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.uiDesigner.binding;

import com.intellij.lang.cacheBuilder.SimpleWordsScanner;
import com.intellij.lang.cacheBuilder.WordOccurrence;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.compiler.AlienFormFileException;
import com.intellij.uiDesigner.compiler.UnexpectedFormElementException;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.lw.IComponent;
import com.intellij.uiDesigner.lw.LwRootContainer;
import com.intellij.util.Processor;
import org.jdom.input.JDOMParseException;
import org.jetbrains.annotations.NotNull;


public class FormWordsScanner extends SimpleWordsScanner {
  private static final Logger LOG = Logger.getInstance(FormWordsScanner.class);

  @Override
  public void processWords(@NotNull CharSequence fileText, final @NotNull Processor<? super WordOccurrence> processor) {
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
                                @Override
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
    catch(AlienFormFileException | JDOMParseException | UnexpectedFormElementException ex) {
      // ignore
    }
    catch (Exception e) {
      LOG.error("Error indexing form file", e);
    }
  }

  private static void processClassAndPackagesNames(String qName, final Processor<? super WordOccurrence> processor) {
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
