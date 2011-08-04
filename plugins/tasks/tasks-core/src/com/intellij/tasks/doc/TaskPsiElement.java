/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.tasks.doc;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.FakePsiElement;
import com.intellij.tasks.Task;

/**
 * @author Dennis.Ushakov
 */
public class TaskPsiElement extends FakePsiElement {
  private final PsiManager myPsiManager;
  private final Task myTask;

  public TaskPsiElement(PsiManager psiManager, final Task task) {
    myPsiManager = psiManager;
    myTask = task;
  }

  @Override
  public PsiElement getParent() {
    return null;
  }

  public Task getTask() {
    return myTask;
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public PsiManager getManager() {
    return myPsiManager;
  }

  @Override
  public PsiFile getContainingFile() {
    return PsiFileFactory.getInstance(getProject()).createFileFromText("foo.txt", "");
  }

  @Override
  public String getName() {
    return myTask.getPresentableName();
  }
}
