package com.intellij.openapi.roots.ui.configuration.libraryEditor;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.project.ProjectBundle;

import javax.swing.*;

class JavadocElementDescriptor extends NodeDescriptor<JavadocElement> {
    private final JavadocElement myElement;
    public static final Icon ICON = IconLoader.getIcon("/nodes/javaDocFolder.png");

    public JavadocElementDescriptor(NodeDescriptor parentDescriptor, JavadocElement element) {
      super(null, parentDescriptor);
      myElement = element;
      myOpenIcon = myClosedIcon = ICON;
    }

    public boolean update() {
      myName = ProjectBundle.message("library.javadocs.node");
      return false;
    }

    public JavadocElement getElement() {
      return myElement;
    }
  }
