package org.jetbrains.idea.svn.api;

import com.intellij.openapi.util.Version;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.WorkingCopyFormat;

/**
 * @author Konstantin Kolosovsky.
 */
public class SvnKitVersionClient extends BaseSvnClient implements VersionClient {

  @NotNull
  @Override
  public Version getVersion() {
    return WorkingCopyFormat.ONE_DOT_SEVEN.getVersion();
  }
}
