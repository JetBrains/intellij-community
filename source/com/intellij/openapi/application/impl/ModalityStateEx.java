package com.intellij.openapi.application.impl;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.ProgressIndicator;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

public class ModalityStateEx extends ModalityState {
  private static final WeakReference[] EMPTY_REFS_ARRAY = new WeakReference[0];

  private final WeakReference[] myModalEntities;

  ModalityStateEx(Object[] modalEntities) {
    if (modalEntities.length > 0) {
      myModalEntities = new WeakReference[modalEntities.length];
      for (int i = 0; i < modalEntities.length; i++) {
        Object entity = modalEntities[i];
        myModalEntities[i] = new WeakReference(entity);
      }
    }
    else{
      myModalEntities = EMPTY_REFS_ARRAY;
    }
  }

  public ModalityState appendProgress(ProgressIndicator progress){
    return appendEnitity(progress);
  }

  ModalityStateEx appendEnitity(Object anEntity){
    ArrayList list = new ArrayList();
    for (int i = 0; i < myModalEntities.length; i++) {
      Object entity = myModalEntities[i].get();
      if (entity == null) continue;
      list.add(entity);
    }
    list.add(anEntity);
    return new ModalityStateEx(list.toArray());
  }

  private static boolean contains(WeakReference[] array, Object o){
    for (int i = 0; i < array.length; i++) {
      Object o1 = array[i].get();
      if (o1 == null) continue;
      if (o1.equals(o)) return true;
    }
    return false;
  }

  public boolean dominates(ModalityState anotherState){
    for (int i = 0; i < myModalEntities.length; i++) {
      Object entity = myModalEntities[i].get();
      if (entity == null) continue;
      if (!contains(((ModalityStateEx)anotherState).myModalEntities, entity)) return true; // I have entity which is absent in anotherState
    }
    return false;
  }

  public String toString() {
    if (myModalEntities.length == 0) return "ModalityState.NON_MODAL";
    StringBuffer buffer = new StringBuffer();
    buffer.append("ModalityState:");
    for (int i = 0; i < myModalEntities.length; i++) {
      Object entity = myModalEntities[i].get();
      if (i > 0) buffer.append(", ");
      buffer.append(entity);
    }
    return buffer.toString();
  }
}