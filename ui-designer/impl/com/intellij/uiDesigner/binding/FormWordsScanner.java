/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
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
                                public boolean visit(IComponent iComponent) {
                                  String componentClassName = iComponent.getComponentClassName();
                                  processClassAndPackagesNames(componentClassName, processor);
                                  final String binding = iComponent.getBinding();
                                  if (binding != null) {
                                    processor.process(new WordOccurrence(binding, WordOccurrence.Kind.FOREIGN_LANGUAGE));
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
    catch (Exception e) {
      LOG.error("Error indexing form file", e);
    }
  }

  private static void processClassAndPackagesNames(String qName, final Processor<WordOccurrence> processor) {
    processor.process(new WordOccurrence(qName, WordOccurrence.Kind.FOREIGN_LANGUAGE));
    int idx = qName.lastIndexOf('.');
    while (idx > 0) {
      qName = qName.substring(0, idx);
      processor.process(new WordOccurrence(qName, WordOccurrence.Kind.FOREIGN_LANGUAGE));
      idx = qName.lastIndexOf('.');
    }
  }
}
