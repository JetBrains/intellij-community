/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python.sdk;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Ref;
import com.intellij.remote.RemoteCredentialsHolder;
import com.intellij.remote.RemoteSdkAdditionalData;
import com.intellij.remote.VagrantBasedCredentialsHolder;
import com.intellij.remote.WebDeploymentCredentialsHolder;
import com.intellij.remote.ext.CredentialsCase;
import com.intellij.remote.ext.LanguageCaseCollector;
import com.intellij.util.ObjectUtils;
import com.jetbrains.python.remote.PyCredentialsContribution;
import org.jetbrains.annotations.Nullable;

public abstract class CredentialsTypeExChecker {
  private boolean mySshContribution;
  private boolean myVagrantContribution;
  private boolean myWebDeploymentContribution;

  public CredentialsTypeExChecker withSshContribution(boolean sshContribution) {
    mySshContribution = sshContribution;
    return this;
  }

  public CredentialsTypeExChecker withVagrantContribution(boolean vagrantContribution) {
    myVagrantContribution = vagrantContribution;
    return this;
  }

  public CredentialsTypeExChecker withWebDeploymentContribution(boolean webDeploymentContribution) {
    myWebDeploymentContribution = webDeploymentContribution;
    return this;
  }

  public boolean check(@Nullable final Sdk sdk) {
    if (sdk == null) {
      return false;
    }
    RemoteSdkAdditionalData data = ObjectUtils.tryCast(sdk.getSdkAdditionalData(), RemoteSdkAdditionalData.class);
    if (data == null) {
      return false;
    }
    return check(data);
  }

  public boolean check(RemoteSdkAdditionalData data) {
    final Ref<Boolean> result = Ref.create(mySshContribution);
    data.switchOnConnectionType(new LanguageCaseCollector<PyCredentialsContribution>() {

      @Override
      protected void processLanguageContribution(PyCredentialsContribution languageContribution, Object credentials) {
        result.set(checkLanguageContribution(languageContribution));
      }
    }.collectCases(PyCredentialsContribution.class, new CredentialsCase.Ssh() {
      @Override
      public void process(RemoteCredentialsHolder credentials) {
        result.set(mySshContribution);
      }
    }, new CredentialsCase.Vagrant() {
      @Override
      public void process(VagrantBasedCredentialsHolder credentials) {
        result.set(myVagrantContribution);
      }
    }, new CredentialsCase.WebDeployment() {
      @Override
      public void process(WebDeploymentCredentialsHolder credentials) {
        result.set(myWebDeploymentContribution);
      }
    }));
    return result.get();
  }

  protected abstract boolean checkLanguageContribution(PyCredentialsContribution languageContribution);
}
