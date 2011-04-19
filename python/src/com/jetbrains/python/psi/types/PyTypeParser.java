package com.jetbrains.python.psi.types;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author yole
 */
public class PyTypeParser {
  private PyTypeParser() {
  }

  @Nullable
  public static PyType getTypeByName(PsiElement anchor, String type) {
    if (type == null) {
      return null;
    }

    if (type.startsWith("(") && type.endsWith(")")) {
      return parseTupleType(anchor, type.substring(1, type.length()-1));
    }

    final PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(anchor);

    if (type.equals("string")) {
      return builtinCache.getStrType();
    }
    if (type.equals("file object")) {
      return builtinCache.getObjectType("file");
    }
    if (type.equals("dictionary")) {
      return builtinCache.getObjectType("dict");
    }
    if (type.startsWith("list of")) {
      return parseListType(anchor, type.substring(7).trim());
    }
    if (type.startsWith("dict from")) {
      return parseDictType(anchor, type.substring(9).trim());
    }
    if (type.equals("integer")) {
      return builtinCache.getIntType();
    }
    final PyClassType classType = builtinCache.getObjectType(type);
    if (classType != null) {
      return classType;
    }

    final PsiFile anchorFile = anchor.getContainingFile();
    if (anchor instanceof PyFile) {
      final PyClass aClass = ((PyFile)anchorFile).findTopLevelClass(type);
      if (aClass != null) {
        return new PyClassType(aClass, false);
      }
    }

    return null;
  }

  private static PyType parseTupleType(PsiElement anchor, String elementTypeNames) {
    final List<String> elements = StringUtil.split(elementTypeNames, ",");
    PyType[] elementTypes = new PyType[elements.size()];
    for (int i = 0; i < elementTypes.length; i++) {
      elementTypes [i] = getTypeByName(anchor, elements.get(i).trim());
    }
    return new PyTupleType(anchor, elementTypes);
  }

  private static PyType parseListType(PsiElement anchor, String elementTypeName) {
    PyClass list = PyBuiltinCache.getInstance(anchor).getClass("list");
    PyType elementType = getTypeByName(anchor, elementTypeName);
    return new PyCollectionTypeImpl(list, false, elementType);
  }

  @Nullable
  private static PyType parseDictType(PsiElement anchor, String fromToTypeNames) {
    int pos = fromToTypeNames.indexOf(" to ");
    if (pos > 0) {
      String toTypeName = fromToTypeNames.substring(pos + 4).trim();
      PyClass dict = PyBuiltinCache.getInstance(anchor).getClass("dict");
      return new PyCollectionTypeImpl(dict, false, getTypeByName(anchor, toTypeName));
    }
    return PyBuiltinCache.getInstance(anchor).getDictType();
  }

}
