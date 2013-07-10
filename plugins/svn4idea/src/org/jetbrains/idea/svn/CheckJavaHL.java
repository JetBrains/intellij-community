/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.idea.svn;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.project.Project;
import org.apache.subversion.javahl.types.Version;

/**
 * Created by IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 1/24/12
 * Time: 2:20 PM
 */
public class CheckJavaHL {
  public static final String VERSION_CLASS = "org.apache.subversion.javahl.types.Version";
  public static final String CHECK_JAVAHL_VERSION = "svn.check.javahl.version";
  private static boolean ourIsPresent;
  private static String ourProblemDescription;

  static {
    try {
      ClassLoader loader = CheckJavaHL.class.getClassLoader();
      if (loader == null) {
        loader = ClassLoader.getSystemClassLoader();
      }
      ourProblemDescription = "No JavaHL library found.";
      if (loader != null) {
        Class<?> aClass = loader.loadClass(VERSION_CLASS);
        if (aClass != null) {
          if (checkVersion(aClass)) {
            ourIsPresent = true;
          }
        }
      }
    } catch (ClassNotFoundException e) {
      ourIsPresent = false;
    }
  }

  private static boolean checkVersion(Class<?> aClass) {
    try {
      Version v = (Version)aClass.newInstance();
      boolean atLeast = true;
      Boolean check = Boolean.getBoolean(CHECK_JAVAHL_VERSION);
      if (Boolean.TRUE.equals(check)) {
        atLeast = v.isAtLeast(1, 7, 0);
        if (! atLeast) {
          ourProblemDescription = "JavaHL library version is old: " + v.toString();
        }
      }
      return atLeast;
    }
    catch (InstantiationException e) {
      return false;
    }
    catch (IllegalAccessException e) {
      return false;
    } catch (Throwable e) {
      return false;
    }
  }

  public static boolean isPresent() {
    return ourIsPresent;
  }

  public static String getProblemDescription() {
    return ourProblemDescription;
  }

  public static void runtimeCheck(final Project project) {
    if (! ourIsPresent) {
      Notifications.Bus.notify(new Notification(SvnVcs.getInstance(project).getDisplayName(), "Subversion: JavaHL problem",
                                                ourProblemDescription + " Acceleration is not available.",
                                                NotificationType.ERROR), project);
    }
  }
}
