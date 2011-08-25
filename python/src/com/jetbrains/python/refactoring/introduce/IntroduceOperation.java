package com.jetbrains.python.refactoring.introduce;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.psi.PyExpression;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class IntroduceOperation {
  private final Project myProject;
  private final Editor myEditor;
  private final PsiFile myFile;
  private String myName;
  private IntroduceHandler.InitPlace myInitPlace = IntroduceHandler.InitPlace.SAME_METHOD;
  private boolean myReplaceAll;
  private final boolean myHasConstructor;
  private final boolean myTestClass;
  private PsiElement myElement;
  private PyExpression myInitializer;
  private List<PsiElement> myOccurrences = Collections.emptyList();
  private Collection<String> mySuggestedNames;

  public IntroduceOperation(Project project,
                            Editor editor,
                            PsiFile file,
                            String name,
                            boolean replaceAll,
                            boolean hasConstructor,
                            boolean testClass) {
    myProject = project;
    myEditor = editor;
    myFile = file;
    myName = name;
    myReplaceAll = replaceAll;
    myHasConstructor = hasConstructor;
    myTestClass = testClass;
  }

  public String getName() {
    return myName;
  }

  public void setName(String name) {
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

  public boolean isReplaceAll() {
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

  public boolean hasConstructor() {
    return myHasConstructor;
  }

  public boolean isTestClass() {
    return myTestClass;
  }
}
