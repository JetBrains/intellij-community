package org.jetbrains.plugins.ipnb.configuration;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableProvider;
import com.intellij.openapi.project.Project;
import com.intellij.util.PlatformUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class IpnbConfigurableProvider extends ConfigurableProvider {
  private final Project myProject;

  public IpnbConfigurableProvider(@NotNull Project project) {
    myProject = project;
  }

  @Nullable
  @Override
  public Configurable createConfigurable() {
    return PlatformUtils.isPyCharmPro() || PlatformUtils.isIdeaUltimate() ? new IpnbConfigurable(myProject) : null;
  }

  @Override
  public boolean canCreateConfigurable() {
    return PlatformUtils.isPyCharmPro() || PlatformUtils.isIdeaUltimate();
  }
}
