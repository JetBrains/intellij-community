package com.jetbrains.python.validation;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.psi.*;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

import java.io.FileReader;
import java.io.IOException;
import java.util.*;

/**
 * User: catherine
 */
public class UnsupportedFeaturesUtil {
  public static Map<LanguageLevel, Set<String>> BUILTINS = new HashMap<LanguageLevel, Set<String>>();
  public static Map<LanguageLevel, Set<String>> MODULES = new HashMap<LanguageLevel, Set<String>>();

  public static Vector<String> ALL_LANGUAGE_LEVELS;
  static {
    try {
      fillMaps();
    }
    catch (IOException e) {
      Logger log = Logger.getInstance(UnsupportedFeaturesUtil.class.getName());
      log.error("Cannot find \"versions.xml\". " + e.getMessage());
    }
    ALL_LANGUAGE_LEVELS = new Vector<String>();
    ALL_LANGUAGE_LEVELS.add(LanguageLevel.PYTHON24.toString());
    ALL_LANGUAGE_LEVELS.add(LanguageLevel.PYTHON25.toString());
    ALL_LANGUAGE_LEVELS.add(LanguageLevel.PYTHON26.toString());
    ALL_LANGUAGE_LEVELS.add(LanguageLevel.PYTHON27.toString());
    ALL_LANGUAGE_LEVELS.add(LanguageLevel.PYTHON30.toString());
    ALL_LANGUAGE_LEVELS.add(LanguageLevel.PYTHON31.toString());
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

  public static boolean raiseHasNoArgs(PyRaiseStatement node, LanguageLevel versionToProcess) {
    final PyExpression[] expressions = node.getExpressions();
    if (expressions.length == 0 && versionToProcess.isPy3K()) {
      final PyExceptPart exceptPart = PsiTreeUtil.getParentOfType(node, PyExceptPart.class);
      if (exceptPart == null) {
        return true;
      }
    }
    return false;
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

  public static boolean visitPyListCompExpression(final PyListCompExpression node, LanguageLevel versionToProcess) {
    final List<ComprhForComponent> forComponents = node.getForComponents();
    if (versionToProcess.isPy3K()) {
      for (ComprhForComponent forComponent : forComponents) {
        final PyExpression iteratedList = forComponent.getIteratedList();
        if (iteratedList instanceof PyTupleExpression) {
          return true;
        }
      }
    }
    return false;
  }
}

