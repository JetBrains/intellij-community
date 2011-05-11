package com.jetbrains.python;

import com.intellij.lang.Language;
import com.intellij.psi.tree.IStubFileElementType;
import com.jetbrains.python.psi.PyFileElementType;
import com.jetbrains.python.validation.*;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * @author yole
 */
public class PythonLanguage extends Language {
  private final IStubFileElementType ELTYPE_FILE;
  private final Set<Class<? extends PyAnnotator>> _annotators = new CopyOnWriteArraySet<Class<? extends PyAnnotator>>();

  public static PythonLanguage getInstance() {
    return (PythonLanguage) PythonFileType.INSTANCE.getLanguage();
  }

  @Override
  public boolean isCaseSensitive() {
    return true; // http://jetbrains-feed.appspot.com/message/372001
  }

  {
    _annotators.add(AssignTargetAnnotator.class);
    _annotators.add(ParameterListAnnotator.class);
    _annotators.add(ReturnAnnotator.class);
    _annotators.add(TryExceptAnnotator.class);
    _annotators.add(BreakContinueAnnotator.class);
    _annotators.add(GlobalAnnotator.class);
    _annotators.add(ImportAnnotator.class);
    _annotators.add(UnicodeOrByteLiteralAnnotator.class);
    _annotators.add(StringLiteralQuotesAnnotator.class);
    _annotators.add(PyBuiltinAnnotator.class);
    _annotators.add(UnsupportedFeatures.class);
  }


  protected PythonLanguage() {
    super("Python");
    ELTYPE_FILE = new PyFileElementType(this);
  }

  public IStubFileElementType getFileElementType() {
    return ELTYPE_FILE;
  }

  public Set<Class<? extends PyAnnotator>> getAnnotators() {
    return _annotators;
  }
}
