package com.intellij.tasks.generic;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.PostMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Mikhail Golubev
 */
public class GenericRepositoryUtil {
  private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{(\\w[-\\w]*)\\}");
  private static SimpleDateFormat ISO8601_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
  private static Pattern ISO8601_DATE_PATTERN = Pattern.compile(
    "(\\d{4}-\\d{2}-\\d{2})[ T](\\d{2}:\\d{2}:\\d{2})(.\\d{3,})?([+-]\\d{2}:\\d{2}|[+-]\\d{4}|[+-]\\d{2}|Z)");

  public static HttpMethod getPostMethodFromURL(final String requestUrl) {
    int n = requestUrl.indexOf('?');
    if (n == -1) {
      return new PostMethod(requestUrl);
    }
    PostMethod postMethod = new PostMethod(requestUrl.substring(0, n));
    String[] queryParams = requestUrl.substring(n + 1).split("&");
    postMethod.addParameters(ContainerUtil.map2Array(queryParams, NameValuePair.class, new Function<String, NameValuePair>() {
      @Override
      public NameValuePair fun(String s) {
        String[] nv = s.split("=");
        if (nv.length == 1) {
          return new NameValuePair(nv[0], "");
        }
        return new NameValuePair(nv[0], nv[1]);
      }
    }));
    return postMethod;
  }

  public static String substituteTemplateVariables(String s, Collection<TemplateVariable> variables) {
    Map<String, String> lookup = new HashMap<String, String>();
    for (TemplateVariable v : variables) {
      lookup.put(v.getName(), v.getValue());
    }
    StringBuffer sb = new StringBuffer();
    Matcher m = PLACEHOLDER_PATTERN.matcher(s);
    while (m.find()) {
      String replacement = lookup.containsKey(m.group(1)) ? lookup.get(m.group(1)) : m.group(0);
      m.appendReplacement(sb, replacement);
    }
    return m.appendTail(sb).toString();
  }

  public static List<String> createPlaceholdersList(GenericRepository repository) {
    return createPlaceholdersList(repository.getAllTemplateVariables());
  }

  public static List<String> createPlaceholdersList(List<TemplateVariable> variables) {
    return ContainerUtil.map2List(variables, new Function<TemplateVariable, String>() {
      @Override
      public String  fun(TemplateVariable variable) {
        return String.format("{%s}", variable.getName());
      }
    });
  }

  @Nullable
  public static Date parseISO8601Date(@NotNull String s) {
    // SimpleDateFormat prior JDK7 doesn't support 'X' specifier for ISO 8601 timezone format.
    // Because some bug trackers and task servers e.g. send dates ending with 'Z' (that stands for UTC),
    // dates should be preprocessed before parsing.
    Matcher m = ISO8601_DATE_PATTERN.matcher(s);
    if (!m.matches()) {
      return null;
    }
    String datePart = m.group(1);
    String timePart = m.group(2);
    String milliseconds = m.group(3);
    milliseconds = milliseconds == null? "000" : milliseconds.substring(1, 4);
    String timezone = m.group(4);
    if (timezone.equals("Z")) {
      timezone = "+0000";
    } else if (timezone.length() == 3) {
      // [+-]HH
      timezone += "00";
    } else if (timezone.length() == 6) {
      // [+-]HH:MM
      timezone = timezone.substring(0, 3) + timezone.substring(4, 6);
    }
    String canonicalForm = String.format("%sT%s.%s%s", datePart, timePart, milliseconds, timezone);
    try {
      return ISO8601_DATE_FORMAT.parse(canonicalForm);
    }
    catch (ParseException e) {
      return null;
    }
  }

  public static String prettifyVariableName(String variableName) {
    String prettyName = variableName.replace('_', ' ');
    return StringUtil.capitalizeWords(prettyName, true);
  }

  public static <T> List<T> concat(List<? extends T> list, T... values) {
    return ContainerUtil.concat(true, list, values);
  }
}
