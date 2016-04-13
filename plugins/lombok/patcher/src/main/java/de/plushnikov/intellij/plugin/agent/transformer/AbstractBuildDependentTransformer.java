package de.plushnikov.intellij.plugin.agent.transformer;

import de.plushnikov.intellij.plugin.agent.IdeaPatcherOptionsHolder;
import de.plushnikov.intellij.plugin.agent.support.BuildNumber;
import de.plushnikov.intellij.plugin.agent.support.SupportedBuild;
import de.plushnikov.intellij.util.StringUtils;

/**
 * @author Alexej Kubarev
 */
abstract class AbstractBuildDependentTransformer implements IdeaPatcherTransformer {

  protected boolean isSupportedBuild() {

    SupportedBuild supportedBuild = this.getClass().getAnnotation(SupportedBuild.class);
    if (null != supportedBuild) {
      String ideaBuildString = IdeaPatcherOptionsHolder.getInstance().getOption("ideaBuild");
      BuildNumber currentBuild = BuildNumber.fromString(ideaBuildString);

      String maxString = supportedBuild.max();
      String minString = supportedBuild.min();

      if (!StringUtils.isEmpty(minString)) {
        BuildNumber minBuild = BuildNumber.fromString(minString);
        // minBuild > currentBuild
        if (minBuild.compareTo(currentBuild) > 0) {
          return false;
        }
      }

      if (!StringUtils.isEmpty(maxString)) {
        BuildNumber maxBuild = BuildNumber.fromString(maxString);
        // maxBuild < currentBuild
        if (maxBuild.compareTo(currentBuild) < 0) {
          return false;
        }
      }
    }

    return true;

  }

  @Override
  public boolean supported() {
    return isSupportedBuild();
  }
}
