package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.projectRoots.ProjectRootType;
import com.intellij.openapi.projectRoots.ex.ProjectRoot;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;

/**
 * @author mike
 */
public class ProjectRootUtil {
  public static final String SIMPLE_ROOT = "simple";
  public static final String COMPOSITE_ROOT = "composite";
  /**
   * @deprecated
   */
  public static final String JDK_ROOT = "jdk";
  /**
   * @deprecated
   */
  public static final String OUTPUT_ROOT = "output";
  /**
   * @deprecated
   */
  public static final String EXCLUDED_OUTPUT = "excludedOutput";
  /**
   * @deprecated
   */
  public static final String LIBRARY_ROOT = "library";
  /**
   * @deprecated
   */
  public static final String EJB_ROOT = "ejb";

  static ProjectRoot read(Element element) throws InvalidDataException {
    final String type = element.getAttributeValue("type");

    if (type.equals(SIMPLE_ROOT)) {
      final SimpleProjectRoot root = new SimpleProjectRoot();
      root.readExternal(element);
      return root;
    }
    else if (type.equals(COMPOSITE_ROOT)) {
      final CompositeProjectRoot root = new CompositeProjectRoot();
      root.readExternal(element);
      return root;
    }
    throw new IllegalArgumentException("Wrong type: " + type);
  }

  static Element write(ProjectRoot projectRoot) throws WriteExternalException {
    Element element = new Element("root");
    if (projectRoot instanceof SimpleProjectRoot) {
      element.setAttribute("type", SIMPLE_ROOT);
      ((JDOMExternalizable)projectRoot).writeExternal(element);
    }
    else if (projectRoot instanceof CompositeProjectRoot) {
      element.setAttribute("type", COMPOSITE_ROOT);
      ((CompositeProjectRoot)projectRoot).writeExternal(element);
    }
    else {
      throw new IllegalArgumentException("Wrong root: " + projectRoot);
    }

    return element;
  }

  public static String typeToString(ProjectRootType type) {
    if (type == ProjectRootType.SOURCE) {
      return "sourcePath";
    }
    if (type == ProjectRootType.CLASS) {
      return "classPath";
    }
    if (type == ProjectRootType.JAVADOC) {
      return "javadocPath";
    }
    if (type == ProjectRootType.PROJECT) {
      return "projectPath";
    }
    if (type == ProjectRootType.EXCLUDE) {
      return "excludePath";
    }

    throw new IllegalArgumentException("Wrong type: " + type);
  }

  public static ProjectRootType stringToType(String s) {
    if (s.equals("sourcePath")) {
      return ProjectRootType.SOURCE;
    }
    if (s.equals("classPath")) {
      return ProjectRootType.CLASS;
    }
    if (s.equals("javadocPath")) {
      return ProjectRootType.JAVADOC;
    }
    if (s.equals("projectPath")) {
      return ProjectRootType.PROJECT;
    }
    if (s.equals("excludePath")) {
      return ProjectRootType.EXCLUDE;
    }

    throw new IllegalArgumentException("Wrong type: " + s);
  }
}
