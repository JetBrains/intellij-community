// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.properties;

import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.commandLine.SvnBindException;

import java.io.File;

public interface PropertyConsumer {
  void handleProperty(File path, PropertyData property) throws SvnBindException;

  void handleProperty(Url url, PropertyData property) throws SvnBindException;

  void handleProperty(long revision, PropertyData property) throws SvnBindException;
}
