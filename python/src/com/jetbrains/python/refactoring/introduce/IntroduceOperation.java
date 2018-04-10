/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.refactoring.introduce;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.psi.PyExpression;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

/**
 * @author yole
 */
public class IntroduceOperation {
  private final Project myProject;
  private final Editor myEditor;
  private final PsiFile myFile;
  private String myName;
  private final EnumSet<IntroduceHandler.InitPlace> myAvailableInitPlaces = EnumSet.of(IntroduceHandler.InitPlace.SAME_METHOD);
  private IntroduceHandler.InitPlace myInitPlace = IntroduceHandler.InitPlace.SAME_METHOD;
  private IntroduceHandler.InitPlace myInplaceInitPlace = IntroduceHandler.InitPlace.SAME_METHOD;
  private Boolean myReplaceAll;
  private PsiElement myElement;
  private PyExpression myInitializer;
  private List<PsiElement> myOccurrences = Collections.emptyList();
  private Collection<String> mySuggestedNames;

  public IntroduceOperation(Project project,
                            Editor editor,
                            PsiFile file,
                            String name) {
    myProject = project;
    myEditor = editor;
    myFile = file;
    myName = name;
  }

  public void addAvailableInitPlace(IntroduceHandler.InitPlace initPlace) {
    myAvailableInitPlaces.add(initPlace);
  }

  public void removeAvailableInitPlace(IntroduceHandler.InitPlace initPlace) {
    myAvailableInitPlaces.remove(initPlace);
  }

  public EnumSet<IntroduceHandler.InitPlace> getAvailableInitPlaces() {
    return myAvailableInitPlaces;
  }

  public String getName() {
    return myName;
  }

  public void setName(@Nullable String name) {
    myName = name;
  }

  public Project getProject() {
    return myProject;
  }

  public Editor getEditor() {
    return myEditor;
  }

  public PsiFile getFile() {
    return myFile;
  }

  public PsiElement getElement() {
    return myElement;
  }

  public void setElement(PsiElement element) {
    myElement = element;
  }

  public IntroduceHandler.InitPlace getInitPlace() {
    return myInitPlace;
  }

  public void setInitPlace(IntroduceHandler.InitPlace initPlace) {
    myInitPlace = initPlace;
  }

  public Boolean isReplaceAll() {
    return myReplaceAll;
  }

  public void setReplaceAll(boolean replaceAll) {
    myReplaceAll = replaceAll;
  }

  public PyExpression getInitializer() {
    return myInitializer;
  }

  public void setInitializer(PyExpression initializer) {
    myInitializer = initializer;
  }

  public List<PsiElement> getOccurrences() {
    return myOccurrences;
  }

  public void setOccurrences(List<PsiElement> occurrences) {
    myOccurrences = occurrences;
  }

  public Collection<String> getSuggestedNames() {
    return mySuggestedNames;
  }

  public void setSuggestedNames(Collection<String> suggestedNames) {
    mySuggestedNames = suggestedNames;
  }

  public IntroduceHandler.InitPlace getInplaceInitPlace() {
    return myInplaceInitPlace;
  }

  public void setInplaceInitPlace(IntroduceHandler.InitPlace inplaceInitPlace) {
    myInplaceInitPlace = inplaceInitPlace;
  }
}
