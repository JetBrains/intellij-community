package com.jetbrains.python.psi.impl;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.jetbrains.python.psi.PyFunction;

import java.util.List;

/**
 * Allows to provide a custom list of keyword arguments for a function that uses **kwargs.
 *
 * @author yole
 */
public interface PyKeywordArgumentProvider {
  ExtensionPointName<PyKeywordArgumentProvider> EP_NAME = ExtensionPointName.create("Pythonid.keywordArgumentProvider");

  List<String> getKeywordArguments(PyFunction function);
}
