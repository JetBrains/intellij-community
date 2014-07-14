package com.jetbrains.python;

import com.jetbrains.python.nameResolver.FQNamesProvider;
import org.jetbrains.annotations.NotNull;

/**
 * Python (not django) names
 * @author Ilya.Kazakevich
 */
public class PythonFQDNNames implements FQNamesProvider {

  /**
   * dict()
   */
  public static final PythonFQDNNames DICT_CLASS = new PythonFQDNNames(true, "dict"); // TODO: Add other dict-like types

  private final boolean myIsClass;
  @NotNull
  private final String[] myNames;

  private PythonFQDNNames(final boolean isClass, @NotNull final String... names) {
    myIsClass = isClass;
    myNames = names;
  }

  @NotNull
  @Override
  public String[] getNames() {
    return myNames.clone();
  }

  @Override
  public boolean isClass() {
    return myIsClass;
  }
}
