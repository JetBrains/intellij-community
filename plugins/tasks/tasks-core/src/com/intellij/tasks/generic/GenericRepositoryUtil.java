// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tasks.generic;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.PostMethod;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Mikhail Golubev
 */
public final class GenericRepositoryUtil {
  private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{(\\w[-\\w]*)\\}");

  public static HttpMethod getPostMethodFromURL(final String requestUrl) {
    int n = requestUrl.indexOf('?');
    if (n == -1) {
      return new PostMethod(requestUrl);
    }
    PostMethod postMethod = new PostMethod(requestUrl.substring(0, n));
    String[] queryParams = requestUrl.substring(n + 1).split("&");
    postMethod.addParameters(ContainerUtil.map2Array(queryParams, NameValuePair.class, s -> {
      String[] nv = s.split("=");
      if (nv.length == 1) {
        return new NameValuePair(nv[0], "");
      }
      return new NameValuePair(nv[0], nv[1]);
    }));
    return postMethod;
  }

  public static String substituteTemplateVariables(String s, Collection<? extends TemplateVariable> variables) throws Exception {
    return substituteTemplateVariables(s, variables, true);
  }

  public static String substituteTemplateVariables(String s, Collection<? extends TemplateVariable> variables, boolean escape) throws Exception {
    Map<String, String> lookup = new HashMap<>();
    for (TemplateVariable v : variables) {
      lookup.put(v.getName(), v.getValue());
    }
    StringBuilder sb = new StringBuilder();
    Matcher m = PLACEHOLDER_PATTERN.matcher(s);
    while (m.find()) {
      String name = m.group(1);
      String replacement = lookup.get(name);
      if (replacement == null) {
        throw new Exception((String.format("Template variable '%s' is undefined", name)));
      }
      // TODO: add proper escape|unescape property to template variables
      if (escape && !name.equals(GenericRepository.SERVER_URL)) {
        m.appendReplacement(sb, URLEncoder.encode(replacement, StandardCharsets.UTF_8));
      }
      else {
        m.appendReplacement(sb, replacement);
      }
    }
    return m.appendTail(sb).toString();
  }

  public static List<String> createPlaceholdersList(GenericRepository repository) {
    return createPlaceholdersList(repository.getAllTemplateVariables());
  }

  public static List<String> createPlaceholdersList(List<? extends TemplateVariable> variables) {
    return ContainerUtil.map2List(variables, variable -> String.format("{%s}", variable.getName()));
  }

  public static @NlsContexts.Label String prettifyVariableName(@NlsContexts.Label String variableName) {
    String prettyName = variableName.replace('_', ' ');
    return StringUtil.capitalizeWords(prettyName, true);
  }

  public static <T> List<T> concat(List<? extends T> list, T... values) {
    return ContainerUtil.append(list, values);
  }
}
