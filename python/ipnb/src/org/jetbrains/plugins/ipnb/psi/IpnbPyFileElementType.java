package org.jetbrains.plugins.ipnb.psi;

import com.intellij.lang.Language;
import com.jetbrains.python.psi.PyFileElementType;
import org.jetbrains.annotations.NotNull;

public class IpnbPyFileElementType extends PyFileElementType {
  public IpnbPyFileElementType(Language language) {
    super(language);
  }

  @NotNull
  @Override
  public String getExternalId() {
    return "IpnbFile.Python";
  }
}
