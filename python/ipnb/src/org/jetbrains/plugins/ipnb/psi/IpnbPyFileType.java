package org.jetbrains.plugins.ipnb.psi;

import com.jetbrains.python.PythonFileType;
import org.jetbrains.annotations.NotNull;

public class IpnbPyFileType extends PythonFileType {
  public static PythonFileType INSTANCE = new IpnbPyFileType();

  protected IpnbPyFileType() {
    super(new IpnbPyLanguageDialect());
  }

  @NotNull
  @Override
  public String getName() {
    return "Ipnb Python";
  }

  @NotNull
  @Override
  public String getDescription() {
    return "Ipnb Python";
  }

  @NotNull
  @Override
  public String getDefaultExtension() {
    return "ipnb_py";
  }
}
