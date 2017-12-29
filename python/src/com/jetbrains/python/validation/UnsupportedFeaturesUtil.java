// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.validation;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.XMLReaderFactory;

import java.io.CharArrayWriter;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class UnsupportedFeaturesUtil {
  public static Map<LanguageLevel, Set<String>> BUILTINS = new HashMap<>();
  public static Map<LanguageLevel, Set<String>> MODULES = new HashMap<>();
  public static Map<String, Map<LanguageLevel, Set<String>>> CLASS_METHODS = new HashMap<>();
  public static final List<String> ALL_LANGUAGE_LEVELS = new ArrayList<>();

  static {
    try {
      fillMaps();
      fillTestCaseMethods();
    }
    catch (IOException e) {
      Logger log = Logger.getInstance(UnsupportedFeaturesUtil.class.getName());
      log.error("Cannot find \"versions.xml\". " + e.getMessage());
    }
    for (LanguageLevel level : LanguageLevel.SUPPORTED_LEVELS) {
      ALL_LANGUAGE_LEVELS.add(level.toString());
    }
  }

  private static void fillTestCaseMethods() throws IOException {
    final Logger log = Logger.getInstance(UnsupportedFeaturesUtil.class.getName());
    final FileReader reader = new FileReader(PythonHelpersLocator.getHelperPath("/tools/class_method_versions.xml"));
    try {
      final XMLReader xr = XMLReaderFactory.createXMLReader();
      final ClassMethodsParser parser = new ClassMethodsParser();
      xr.setContentHandler(parser);
      xr.parse(new InputSource(reader));
    }
    catch (SAXException e) {
      log.error("Improperly formed \"class_method_versions.xml\". " + e.getMessage());
    }
    finally {
      reader.close();
    }
  }

  private static void fillMaps() throws IOException {
    Logger log = Logger.getInstance(UnsupportedFeaturesUtil.class.getName());
    FileReader reader = new FileReader(PythonHelpersLocator.getHelperPath("/tools/versions.xml"));
    try {
      XMLReader xr = XMLReaderFactory.createXMLReader();
      VersionsParser parser = new VersionsParser();
      xr.setContentHandler(parser);
      xr.parse(new InputSource(reader));
    }
    catch (SAXException e) {
      log.error("Improperly formed \"versions.xml\". " + e.getMessage());
    }
    finally {
      reader.close();
    }
  }

  public static boolean raiseHasNoArgsUnderFinally(@NotNull PyRaiseStatement node, @NotNull LanguageLevel versionToProcess) {
    return node.getExpressions().length == 0 &&
           versionToProcess.isPython2() &&
           PsiTreeUtil.getParentOfType(node, PyFinallyPart.class) != null;
  }

  public static boolean raiseHasMoreThenOneArg(PyRaiseStatement node, LanguageLevel versionToProcess) {
    final PyExpression[] expressions = node.getExpressions();
    if (expressions.length > 0) {
      if (expressions.length < 2) {
        return false;
      }
      if (versionToProcess.isPy3K()) {
        if (expressions.length == 3) {
          return true;
        }
        PsiElement element = expressions[0].getNextSibling();
        while (element instanceof PsiWhiteSpace) {
          element = element.getNextSibling();
        }
        if (element != null && ",".equals(element.getText())) {
          return true;
        }
      }
    }
    return false;
  }

  public static boolean raiseHasFromKeyword(PyRaiseStatement node, LanguageLevel versionToProcess) {
    final PyExpression[] expressions = node.getExpressions();
    if (expressions.length > 0) {
      if (expressions.length < 2) {
        return false;
      }
      if (versionToProcess.isPython2()) {
        PsiElement element = expressions[0].getNextSibling();
        while (element instanceof PsiWhiteSpace) {
          element = element.getNextSibling();
        }
        if (element != null && element.getNode().getElementType() == PyTokenTypes.FROM_KEYWORD) {
          return true;
        }
      }
    }
    return false;
  }

  public static boolean visitPyListCompExpression(final PyListCompExpression node, LanguageLevel versionToProcess) {
    final List<PyComprehensionForComponent> forComponents = node.getForComponents();
    if (versionToProcess.isPy3K()) {
      for (PyComprehensionForComponent forComponent : forComponents) {
        final PyExpression iteratedList = forComponent.getIteratedList();
        if (iteratedList instanceof PyTupleExpression) {
          return true;
        }
      }
    }
    return false;
  }

  private static class VersionsParser extends DefaultHandler {
    private CharArrayWriter myContent = new CharArrayWriter();
    private LanguageLevel myCurrentLevel;

    public void startElement(String namespaceURI,
                String localName,
                String qName,
                Attributes attr) throws SAXException {
      myContent.reset();
      if (localName.equals("python")) {
        BUILTINS.put(LanguageLevel.fromPythonVersion(attr.getValue("version")), new HashSet<>());
        MODULES.put(LanguageLevel.fromPythonVersion(attr.getValue("version")), new HashSet<>());
        myCurrentLevel = LanguageLevel.fromPythonVersion(attr.getValue("version"));
      }
     }

    public void endElement(String namespaceURI,
              String localName,
              String qName) throws SAXException {
      if (localName.equals("func")) {
        BUILTINS.get(myCurrentLevel).add(myContent.toString());
      }
      if (localName.equals("module")) {
        MODULES.get(myCurrentLevel).add(myContent.toString());
      }
    }

    public void characters(char[] ch, int start, int length)
                                          throws SAXException {
      myContent.write(ch, start, length);
    }
  }

  static class ClassMethodsParser extends DefaultHandler {
    private CharArrayWriter myContent = new CharArrayWriter();
    private String myClassName = "";
    private LanguageLevel myCurrentLevel;

    public void startElement(String namespaceURI,
                             String localName,
                             String qName,
                             Attributes attr) throws SAXException {
      myContent.reset();
      if (localName.equals("class_name")) {
        myClassName = attr.getValue("name");
        if (!CLASS_METHODS.containsKey(myClassName)) {
          CLASS_METHODS.put(myClassName, new HashMap<>());

        }
      }
      if (localName.equals("python")) {
        myCurrentLevel = LanguageLevel.fromPythonVersion(attr.getValue("version"));
        if (myClassName != null) {
          final Map<LanguageLevel, Set<String>> map = CLASS_METHODS.get(myClassName);
          if (map != null)
            map.put(myCurrentLevel, new HashSet<>());
        }
      }
    }

    public void endElement(String namespaceURI,
                           String localName,
                           String qName) throws SAXException {
      if (localName.equals("func")) {
        Map<LanguageLevel, Set<String>> levelSetMap = CLASS_METHODS.get(myClassName);
        if (levelSetMap != null) {
          final Set<String> set = levelSetMap.get(myCurrentLevel);
          if (set != null)
            set.add(myContent.toString());
        }
      }
    }

    public void characters(char[] ch, int start, int length)
      throws SAXException {
      myContent.write(ch, start, length);
    }
  }
}

