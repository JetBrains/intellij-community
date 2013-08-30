package com.jetbrains.python;

import com.jetbrains.python.run.AbstractPyCommonOptionsForm;
import com.jetbrains.python.run.PyCommonOptionsFormData;
import com.jetbrains.python.run.PyCommonOptionsFormFactory;

/**
 * @author yole
 */
public class PyIdeCommonOptionsFormFactory extends PyCommonOptionsFormFactory {
  @Override
  public PyIdeCommonOptionsForm createForm(PyCommonOptionsFormData data) {
    return new PyIdeCommonOptionsForm(data);
  }
}
