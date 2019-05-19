// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server;

import org.eclipse.aether.spi.log.Logger;
import org.eclipse.aether.spi.log.LoggerFactory;

class Maven3WrapperAetherLoggerFactory implements LoggerFactory {
  private Maven3ServerConsoleLogger myConsoleWrapper;

  public Maven3WrapperAetherLoggerFactory(final Maven3ServerConsoleLogger consoleWrapper) {this.myConsoleWrapper = consoleWrapper;}

  @Override
  public Logger getLogger(String s) {
    return new Logger() {
      @Override
      public boolean isDebugEnabled() {
        return myConsoleWrapper.isDebugEnabled();
      }

      @Override
      public void debug(String s) {
        myConsoleWrapper.debug(s);
      }

      @Override
      public void debug(String s, Throwable throwable) {
        myConsoleWrapper.debug(s, throwable);
      }

      @Override
      public boolean isWarnEnabled() {
        return myConsoleWrapper.isWarnEnabled();
      }

      @Override
      public void warn(String s) {
        myConsoleWrapper.warn(s);
      }

      @Override
      public void warn(String s, Throwable throwable) {
        myConsoleWrapper.debug(s, throwable);
      }
    };
  }
}
