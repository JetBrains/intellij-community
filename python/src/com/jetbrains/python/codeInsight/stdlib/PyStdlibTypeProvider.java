package com.jetbrains.python.codeInsight.stdlib;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.HashMap;
import com.jetbrains.python.documentation.StructuredDocString;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.resolve.ResolveImportUtil;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeParser;
import com.jetbrains.python.psi.types.PyTypeProviderBase;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;

/**
 * @author yole
 */
public class PyStdlibTypeProvider extends PyTypeProviderBase {
  private Properties myStdlibTypes2 = new Properties();
  private Properties myStdlibTypes3 = new Properties();
  private Project myProject = null;
  private Map<String, PyType> myTypeCache = new HashMap<String, PyType>();

  @Override
  public PyType getReturnType(PyFunction function, @Nullable PyReferenceExpression callSite, TypeEvalContext context) {
    final String qname = getQualifiedName(function, callSite);
    final String key = String.format("Python%d/%s.return", LanguageLevel.forElement(function).getVersion(), qname);
    final PyType cached = getCachedType(function.getProject(), key);
    if (cached != null) {
      return cached;
    }
    final StructuredDocString docString = getStructuredDocString(qname, LanguageLevel.forElement(function));
    if (docString == null) {
      return null;
    }
    final String s = docString.getReturnType();
    final PyType result = PyTypeParser.getTypeByName(function, s);
    myTypeCache.put(key, result);
    return result;
  }

  @Override
  public PyType getParameterType(PyNamedParameter param, PyFunction func, TypeEvalContext context) {
    final String name = param.getName();
    final String qname = getQualifiedName(func, param);
    final String key = String.format("Python%d/%s.%s", LanguageLevel.forElement(param).getVersion(), qname, name);
    final PyType cached = getCachedType(param.getProject(), key);
    if (cached != null) {
      return cached;
    }
    final StructuredDocString docString = getStructuredDocString(qname, LanguageLevel.forElement(param));
    if (docString == null) {
      return null;
    }
    final String s = docString.getParamType(name);
    final PyType result = PyTypeParser.getTypeByName(func, s);
    myTypeCache.put(key, result);
    return result;
  }

  @Override
  public PyType getIterationType(PyClass iterable) {
    final PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(iterable);
    if (builtinCache.hasInBuiltins(iterable)) {
      if ("file".equals(iterable.getName())) {
        return builtinCache.getStrType();
      }
    }
    return null;
  }

  private StructuredDocString getStructuredDocString(String qualifiedName, LanguageLevel level) {
    final Properties db = getStdlibTypes(level);
    final String docString = db.getProperty(qualifiedName);
    return StructuredDocString.parse(docString);
  }

  private static String getQualifiedName(PyFunction f, PsiElement callSite) {
    String result = f.getName();
    final PyClass c = f.getContainingClass();
    final VirtualFile vfile = f.getContainingFile().getVirtualFile();
    if (vfile != null) {
      final String module = ResolveImportUtil.findShortestImportableName(callSite != null ? callSite : f, vfile);
      result = String.format("%s.%s%s",
                             module,
                             c != null ? c.getName() + "." : "",
                             result);
    }
    return result;
  }

  @Nullable
  private PyType getCachedType(Project project, String key) {
    if (project != myProject) {
      myProject = project;
      myTypeCache.clear();
    }
    return myTypeCache.get(key);
  }

  private Properties getStdlibTypes(LanguageLevel level) {
    final Properties result = level.isPy3K() ? myStdlibTypes3 : myStdlibTypes2;
    final String name = level.isPy3K() ? "StdlibTypes3" : "StdlibTypes2";
    if (result.isEmpty()) {
      InputStream s = getClass().getResourceAsStream(String.format("%s.properties", name));
      try {
        result.load(s);
      }
      catch (IOException ignored) {}
    }
    return result;
  }
}
