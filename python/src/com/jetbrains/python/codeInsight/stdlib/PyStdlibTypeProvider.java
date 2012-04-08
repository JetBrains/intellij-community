package com.jetbrains.python.codeInsight.stdlib;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.documentation.StructuredDocString;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyQualifiedName;
import com.jetbrains.python.psi.impl.PyTypeProvider;
import com.jetbrains.python.psi.resolve.ResolveImportUtil;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.NotNull;
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

  @Nullable
  public static PyStdlibTypeProvider getInstance() {
    for (PyTypeProvider typeProvider : Extensions.getExtensions(PyTypeProvider.EP_NAME)) {
      if (typeProvider instanceof PyStdlibTypeProvider) {
        return (PyStdlibTypeProvider)typeProvider;
      }
    }
    return null;
  }

  @Override
  public PyType getReferenceType(@NotNull PsiElement referenceTarget, @NotNull TypeEvalContext context, @Nullable PsiElement anchor) {
    if (referenceTarget instanceof PyFunction &&
        PyNames.NAMEDTUPLE.equals(((PyFunction) referenceTarget).getName()) &&
        PyNames.COLLECTIONS_PY.equals(referenceTarget.getContainingFile().getName()) &&
        anchor instanceof PyCallExpression) {
      return PyNamedTupleType.fromCall((PyCallExpression)anchor);
    }
    return null;
  }

  @Nullable
  @Override
  public PyType getReturnType(@NotNull PyFunction function, @Nullable PyQualifiedExpression callSite, @NotNull TypeEvalContext context) {
    final String qname = getQualifiedName(function, callSite);
    if (qname != null) {
      if (callSite != null) {
        PyTypeChecker.AnalyzeCallResults results = PyTypeChecker.analyzeCallSite(callSite, context);
        if (results != null) {
          final PyType overloaded = getOverloadedReturnTypeByQName(results.getArguments(), qname, function, context);
          if (overloaded != null) {
            return overloaded;
          }
        }
      }
      return getReturnTypeByQName(qname, function);
    }
    return null;
  }

  @Nullable
  public PyType getConstructorType(@NotNull PyClass cls) {
    final String classQName = cls.getQualifiedName();
    if (classQName != null) {
      final PyQualifiedName canonicalQName = ResolveImportUtil.restoreStdlibCanonicalPath(PyQualifiedName.fromDottedString(classQName));
      if (canonicalQName != null) {
        final PyQualifiedName qname = canonicalQName.append(PyNames.INIT);
        return getReturnTypeByQName(qname.toString(), cls);
      }
    }
    return null;
  }

  @Nullable
  private PyType getReturnTypeByQName(@NotNull String qname, @NotNull PsiElement anchor) {
    final LanguageLevel level = LanguageLevel.forElement(anchor);
    final String key = String.format("Python%d/%s.return", level.getVersion(), qname);
    final PyBuiltinCache cache = PyBuiltinCache.getInstance(anchor);
    final Ref<PyType> cached = cache.getStdlibType(key);
    if (cached != null) {
      return cached.get();
    }
    final StructuredDocString docString = getStructuredDocString(qname, level);
    if (docString == null) {
      return null;
    }
    final String s = docString.getReturnType();
    if (s == null) {
      return null;
    }
    final PyType result = PyTypeParser.getTypeByName(anchor, s);
    cache.storeStdlibType(key, result);
    return result;
  }

  @Nullable
  @Override
  public PyType getParameterType(PyNamedParameter param, @NotNull PyFunction func, @NotNull TypeEvalContext context) {
    final String name = param.getName();
    final String qname = getQualifiedName(func, param);
    if (qname != null && name != null) {
      return getParameterTypeByQName(qname, name, func);
    }
    return null;
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

  @Nullable
  private PyType getOverloadedReturnTypeByQName(@NotNull Map<PyExpression, PyNamedParameter> arguments,
                                                @NotNull String qname,
                                                @NotNull PsiElement anchor,
                                                @NotNull TypeEvalContext context) {
    int i = 1;
    PyType rtype;
    do {
      final String overloadedQName = String.format("%s.%d", qname, i);
      rtype = getReturnTypeByQName(overloadedQName, anchor);
      if (rtype != null) {
        boolean matched = true;
        for (Map.Entry<PyExpression, PyNamedParameter> entry : arguments.entrySet()) {
          final PyNamedParameter p = entry.getValue();
          final String name = p.getName();
          if (p.isPositionalContainer() || p.isKeywordContainer() || name == null) {
            continue;
          }
          PyType argType = entry.getKey().getType(context);
          // Special case for the 'mode' argument of the 'open()' builtin
          if (("__builtin__.open".equals(qname) || "io.open".equals(qname)) && "mode".equals(name)) {
            final PyBuiltinCache cache = PyBuiltinCache.getInstance(anchor);
            final LanguageLevel level = LanguageLevel.forElement(anchor);
            argType = cache.getUnicodeType(level);
            final PyExpression modeExpr = entry.getKey();
            if (modeExpr instanceof PyStringLiteralExpression) {
              final String literal = ((PyStringLiteralExpression)modeExpr).getStringValue();
              if (literal.contains("b")) {
                argType = cache.getBytesType(level);
              }
            }
          }
          final PyType paramType = getParameterTypeByQName(overloadedQName, name, anchor);
          if (!PyTypeChecker.match(paramType, argType, context)) {
            matched = false;
          }
        }
        if (matched) {
          return rtype;
        }
      }
      i++;
    } while (rtype != null);
    return null;
  }

  @Nullable PyType getParameterTypeByQName(@NotNull String functionQName, @NotNull String name, @NotNull PsiElement anchor) {
    final LanguageLevel level = LanguageLevel.forElement(anchor);
    final String key = String.format("Python%d/%s.%s", level.getVersion(), functionQName, name);
    final PyBuiltinCache cache = PyBuiltinCache.getInstance(anchor);
    final Ref<PyType> cached = cache.getStdlibType(key);
    if (cached != null) {
      return cached.get();
    }
    final StructuredDocString docString = getStructuredDocString(functionQName, level);
    if (docString == null) {
      return null;
    }
    final String s = docString.getParamType(name);
    if (s == null) {
      return null;
    }
    final PyType result = PyTypeParser.getTypeByName(anchor, s);
    cache.storeStdlibType(key, result);
    return result;
  }

  @Nullable
  private StructuredDocString getStructuredDocString(String qualifiedName, LanguageLevel level) {
    final Properties db = getStdlibTypes(level);
    final String docString = db.getProperty(qualifiedName);
    if (docString == null && level.isPy3K()) {
      return getStructuredDocString(qualifiedName, LanguageLevel.PYTHON27);
    }
    return StructuredDocString.parse(docString);
  }

  @Nullable
  private static String getQualifiedName(@NotNull PyFunction f, @Nullable PsiElement callSite) {
    if (!f.isValid()) {
      return null;
    }
    String result = f.getName();
    final PyClass c = f.getContainingClass();
    final VirtualFile vfile = f.getContainingFile().getVirtualFile();
    if (vfile != null) {
      String module = ResolveImportUtil.findShortestImportableName(callSite != null ? callSite : f, vfile);
      if ("builtins".equals(module)) {
        module = "__builtin__";
      }
      result = String.format("%s.%s%s",
                             module,
                             c != null ? c.getName() + "." : "",
                             result);
      final PyQualifiedName qname = ResolveImportUtil.restoreStdlibCanonicalPath(PyQualifiedName.fromDottedString(result));
      if (qname != null) {
        return qname.toString();
      }
    }
    return result;
  }

  private Properties getStdlibTypes(LanguageLevel level) {
    final Properties result = level.isPy3K() ? myStdlibTypes3 : myStdlibTypes2;
    final String name = level.isPy3K() ? "StdlibTypes3" : "StdlibTypes2";
    if (result.isEmpty()) {
      try {
        final InputStream s = getClass().getResourceAsStream(String.format("%s.properties", name));
        try {
          result.load(s);
        }
        finally {
          s.close();
        }
      }
      catch (IOException ignored) {}
    }
    return result;
  }
}
