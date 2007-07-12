package com.intellij.openapi.roots.ui.configuration.libraryEditor;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

class AnnotationsElementDescriptor extends NodeDescriptor<AnnotationElement> {
    private final AnnotationElement myElement;
    public static final Icon ICON = IconLoader.getIcon("/modules/annotation.png");

    public AnnotationsElementDescriptor(NodeDescriptor parentDescriptor, AnnotationElement element) {
      super(null, parentDescriptor);
      myElement = element;
      myOpenIcon = myClosedIcon = ICON;
    }

    public boolean update() {
      myName = ProjectBundle.message("sdk.configure.annotations.tab");
      return false;
    }

    public AnnotationElement getElement() {
      return myElement;
    }
  }