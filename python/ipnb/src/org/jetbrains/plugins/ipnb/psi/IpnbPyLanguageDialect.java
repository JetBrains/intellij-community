package org.jetbrains.plugins.ipnb.psi;

import com.intellij.lang.InjectableLanguage;
import com.intellij.lang.Language;
import com.jetbrains.python.PythonLanguage;

public class IpnbPyLanguageDialect extends Language implements InjectableLanguage {
  public static IpnbPyLanguageDialect getInstance() {
    return (IpnbPyLanguageDialect)IpnbPyFileType.INSTANCE.getLanguage();
  }

  protected IpnbPyLanguageDialect() {
    super(PythonLanguage.getInstance(), "IpnbPython");
  }
}
