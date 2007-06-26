package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.projectRoots.ProjectRootType;
import com.intellij.openapi.projectRoots.ex.ProjectRoot;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

/**
 * @author mike
 */
public class ProjectRootUtil {
  @NonNls public static final String SIMPLE_ROOT = "simple";
  @NonNls public static final String COMPOSITE_ROOT = "composite";
  /**
   * @deprecated
   */
  @NonNls public static final String JDK_ROOT = "jdk";
  /**
   * @deprecated
   */
  @NonNls public static final String OUTPUT_ROOT = "output";
  /**
   * @deprecated
   */
  @NonNls public static final String EXCLUDED_OUTPUT = "excludedOutput";
  /**
   * @deprecated
   */
  @NonNls public static final String LIBRARY_ROOT = "library";
  /**
   * @deprecated
   */
  @NonNls public static final String EJB_ROOT = "ejb";
  @NonNls private static final String ATTRIBUTE_TYPE = "type";
  @NonNls private static final String ELEMENT_ROOT = "root";

  static ProjectRoot read(Element element) throws InvalidDataException {
    final String type = element.getAttributeValue(ATTRIBUTE_TYPE);

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
    Element element = new Element(ELEMENT_ROOT);
    if (projectRoot instanceof SimpleProjectRoot) {
      element.setAttribute(ATTRIBUTE_TYPE, SIMPLE_ROOT);
      ((JDOMExternalizable)projectRoot).writeExternal(element);
    }
    else if (projectRoot instanceof CompositeProjectRoot) {
      element.setAttribute(ATTRIBUTE_TYPE, COMPOSITE_ROOT);
      ((CompositeProjectRoot)projectRoot).writeExternal(element);
    }
    else {
      throw new IllegalArgumentException("Wrong root: " + projectRoot);
    }

    return element;
  }

  @NonNls
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
    if (type == ProjectRootType.ANNOTATIONS) {
      return "annotationsPath";
    }
    if (type == ProjectRootType.PROJECT) {
      return "projectPath";
    }
    if (type == ProjectRootType.EXCLUDE) {
      return "excludePath";
    }

    throw new IllegalArgumentException("Wrong type: " + type);
  }

  public static ProjectRootType stringToType(@NonNls String s) {
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
