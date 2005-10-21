package com.intellij.openapi.ui.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.DialogWrapperPeer;
import com.intellij.openapi.ui.DialogWrapperPeerFactory;

import java.awt.*;

public class DialogWrapperPeerFactoryImpl extends DialogWrapperPeerFactory {
  public DialogWrapperPeer createPeer(DialogWrapper wrapper, Project project, boolean canBeParent) {
    return new DialogWrapperPeerImpl(wrapper, project, canBeParent);
  }

  public DialogWrapperPeer createPeer(DialogWrapper wrapper, boolean canBeParent) {
    return new DialogWrapperPeerImpl(wrapper, canBeParent);
  }

  public DialogWrapperPeer createPeer(DialogWrapper wrapper, Component parent, boolean canBeParent) {
    return new DialogWrapperPeerImpl(wrapper, parent, canBeParent);
  }
}