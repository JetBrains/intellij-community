/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.theoryinpractice.testng.configuration;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import org.testng.remote.strprotocol.TestResultMessage;

/**
 * User: anna
 * Date: 3/15/12
 */
public class TestNGVersionChecker {

  public static boolean isVersionIncompatible(Project project, GlobalSearchScope scope) {
    final String protocolClassMessageClass = TestResultMessage.class.getName();
    final PsiClass psiProtocolClass = JavaPsiFacade.getInstance(project).findClass(protocolClassMessageClass, scope);
    if (psiProtocolClass != null) {
      final String instanceFieldName = "m_instanceName";
      try {
        final boolean userHasNewJar = psiProtocolClass.findFieldByName(instanceFieldName, false) != null;

        boolean ideaHasNewJar = true;
        final Class aClass = Class.forName(protocolClassMessageClass);
        try {
          aClass.getDeclaredField(instanceFieldName);
        }
        catch (NoSuchFieldException e) {
          ideaHasNewJar = false;
        }

        return userHasNewJar != ideaHasNewJar;
      }
      catch (Exception ignore) {
      }
    }
    return false;
  }
}
