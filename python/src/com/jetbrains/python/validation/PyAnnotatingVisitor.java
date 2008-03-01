/*
 *  Copyright 2005 Pythonid Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS"; BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.jetbrains.python.validation;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.PythonLanguage;

import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 10.06.2005
 * Time: 23:23:10
 * To change this template use File | Settings | File Templates.
 */
public class PyAnnotatingVisitor implements Annotator {
    private static final Logger LOGGER
            = Logger.getInstance(PyAnnotatingVisitor.class.getName());
  private Set<? extends Class<? extends PyAnnotator>> _annotators;

  public PyAnnotatingVisitor() {
    _annotators = ((PythonLanguage) PythonFileType.INSTANCE.getLanguage()).getAnnotators();
  }

  public void annotate(PsiElement psiElement, AnnotationHolder holder) {
        for(Class<? extends PyAnnotator> cls: _annotators) {
            PyAnnotator annotator;
            try {
              annotator = cls.newInstance();
            } catch (InstantiationException e) {
              LOGGER.error(e);
              continue;
            } catch (IllegalAccessException e) {
              LOGGER.error(e);
              continue;
            }
            annotator.setHolder(holder);
            psiElement.accept(annotator);
        }
    }
}
