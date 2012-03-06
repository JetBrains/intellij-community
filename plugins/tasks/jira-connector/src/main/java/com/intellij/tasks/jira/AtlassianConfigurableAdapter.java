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
package com.intellij.tasks.jira;

import com.atlassian.theplugin.commons.util.HttpConfigurableAdapter;
import com.intellij.util.net.HttpConfigurable;

/**
* @author Dmitry Avdeev
*         Date: 3/5/12
*/
class AtlassianConfigurableAdapter implements HttpConfigurableAdapter {

  @Override
  public boolean isKeepProxyPassowrd() {
    return HttpConfigurable.getInstance().KEEP_PROXY_PASSWORD;
  }

  @Override
  public boolean isProxyAuthentication() {
    return HttpConfigurable.getInstance().PROXY_AUTHENTICATION;
  }

  @Override
  public boolean isUseHttpProxy() {
    return HttpConfigurable.getInstance().USE_HTTP_PROXY;
  }

  @Override
  public String getPlainProxyPassword() {
    return HttpConfigurable.getInstance().getPlainProxyPassword();
  }

  @Override
  public String getProxyLogin() {
    return HttpConfigurable.getInstance().PROXY_LOGIN;
  }

  @Override
  public int getProxyPort() {
    return HttpConfigurable.getInstance().PROXY_PORT;
  }

  @Override
  public String getProxyHost() {
    return HttpConfigurable.getInstance().PROXY_HOST;
  }

  @Override
  public Object getHTTPProxySettingsDialog() {
    return null;
  }
}
