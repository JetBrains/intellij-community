package com.intellij.openapi.actionSystem.impl;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Presentation;

import java.util.WeakHashMap;

public class PresentationFactory {
  private WeakHashMap myAction2Presentation;

  public PresentationFactory() {
    myAction2Presentation = new WeakHashMap();
  }

  public final Presentation getPresentation(AnAction action){
    if (action == null) {
      throw new IllegalArgumentException("action cannot be null");
    }
    Presentation presentation = (Presentation)myAction2Presentation.get(action);
    if (presentation == null){
      presentation = (Presentation)action.getTemplatePresentation().clone();
      myAction2Presentation.put(action, presentation);
    }
    return presentation;
  }

}
