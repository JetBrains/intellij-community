package com.intellij.openapi.roots.ui.configuration.libraryEditor;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

class ClassesElementDescriptor extends NodeDescriptor<ClassesElement> {
    private final ClassesElement myElement;
    public static final Icon ICON = IconLoader.getIcon("/nodes/compiledClassesFolder.png");

    public ClassesElementDescriptor(NodeDescriptor parentDescriptor, ClassesElement element) {
      super(null, parentDescriptor);
      myElement = element;
      myOpenIcon = myClosedIcon = ICON;
    }

    public boolean update() {
      myName = "Classes";
      return false;
    }

    public ClassesElement getElement() {
      return myElement;
    }
  }
