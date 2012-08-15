/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.maven.embedder;

import org.jetbrains.annotations.NotNull;

public class MavenEmbedderLog {
  public static final MavenEmbedderLogger LOG = new MavenEmbedderLogger() {
    public void debug(final CharSequence msg) {
      if (ourDelegate != null) ourDelegate.debug(msg);
    }

    public void debug(final CharSequence msg, final Throwable e) {
      if (ourDelegate != null) ourDelegate.debug(msg, e);
    }

    public void debug(final Throwable e) {
      if (ourDelegate != null) ourDelegate.debug(e);
    }

    public void info(final CharSequence msg) {
      if (ourDelegate != null) ourDelegate.info(msg);
    }

    public void info(final CharSequence msg, final Throwable e) {
      if (ourDelegate != null) ourDelegate.info(msg, e);
    }

    public void info(final Throwable e) {
      if (ourDelegate != null) ourDelegate.info(e);
    }

    public void warn(final CharSequence msg) {
      if (ourDelegate != null) ourDelegate.warn(msg);
    }

    public void warn(final CharSequence msg, final Throwable e) {
      if (ourDelegate != null) ourDelegate.warn(msg, e);
    }

    public void warn(final Throwable e) {
      if (ourDelegate != null) ourDelegate.warn(e);
    }

    public void error(final CharSequence msg) {
      if (ourDelegate != null) ourDelegate.error(msg);
    }

    public void error(final CharSequence msg, final Throwable e) {
      if (ourDelegate != null) ourDelegate.error(msg, e);
    }

    public void error(final Throwable e) {
      if (ourDelegate != null) ourDelegate.error(e);
    }
  };

  private static MavenEmbedderLogger ourDelegate = null;

  public void setLogger(@NotNull final MavenEmbedderLogger logger) {
    ourDelegate = logger;
  }
}
