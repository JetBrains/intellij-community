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
package org.jetbrains.idea.maven.dom.references;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.util.ProcessingContext;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.MavenPropertyResolver;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.utils.MavenJDOMUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MavenFilteredPropertyPsiReferenceProvider extends PsiReferenceProvider {

  private static final Key<Pattern> KEY = Key.create("MavenFilteredPropertyPsiReferenceProvider:delimitersKey");
  
  public static final Pattern DEFAULT_DELIMITERS = MavenPropertyResolver.PATTERN;

  @NotNull
  public static Pattern getDelimitersPattern(MavenProject mavenProject) {
    Pattern res = mavenProject.getCachedValue(KEY);
    if (res == null) {
      Element cfg = mavenProject.getPluginConfiguration("org.apache.maven.plugins", "maven-resources-plugin");
      if (cfg == null) {
        res = DEFAULT_DELIMITERS;
      }
      else {
        List<String> delimiters = MavenJDOMUtil.findChildrenValuesByPath(cfg, "delimiters", "delimiter");
        if (delimiters.isEmpty() || delimiters.size() > 10) {
          res = DEFAULT_DELIMITERS;
        }
        else {
          StringBuilder patternBuilder = new StringBuilder();
          
          for (String delimiter : delimiters) {
            delimiter = delimiter.trim();
            if (delimiter.isEmpty()) continue;

            int ind = delimiter.indexOf('*');
            if (ind >= 0) {
              appendDelimiter(patternBuilder, delimiter.substring(0, ind), delimiter.substring(ind + 1));
            }
            else {
              appendDelimiter(patternBuilder, delimiter, delimiter);
            }
          }

          // <useDefaultDelimiters> is not used if custom delimiters are not present.
          boolean useDefaultDelimiters = true;

          String useDefaultDelimitersText = cfg.getChildText("useDefaultDelimiters");
          if (StringUtil.isNotEmpty(useDefaultDelimitersText)) {
            useDefaultDelimiters = Boolean.parseBoolean(useDefaultDelimitersText);
          }

          if (useDefaultDelimiters) {
            appendDelimiter(patternBuilder, "${", "}");
            appendDelimiter(patternBuilder, "@", "@");
          }

          res = Pattern.compile(patternBuilder.toString());
        }
      }
  
      res = mavenProject.putCachedValue(KEY, res);
    }

    return res;
  }
  
  private static void appendDelimiter(StringBuilder pattern, String prefix, String suffix) {
    if (pattern.length() > 0) {
      pattern.append('|');
    }
    pattern.append(Pattern.quote(prefix)).append("(.+?)").append(Pattern.quote(suffix));
  }

  private static boolean shouldAddReference(@NotNull PsiElement element) {
    if (element.getFirstChild() == element.getLastChild()) {
      return true; // Add to all leaf elements
    }

    if (element instanceof XmlAttribute) {
      return true;
    }

    return false; // Don't add references to all element to avoid performance problem.
  }

  @NotNull
  @Override
  public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
    if (!shouldAddReference(element)) {
      // Add reference to element with one child or leaf element only to avoid performance problem.
      return PsiReference.EMPTY_ARRAY;
    }

    if (!MavenDomUtil.isFilteredResourceFile(element)) return PsiReference.EMPTY_ARRAY;

    String text = element.getText();
    if (StringUtil.isEmptyOrSpaces(text)) return PsiReference.EMPTY_ARRAY;

    MavenProject mavenProject = MavenDomUtil.findContainingProject(element);
    if (mavenProject == null) return PsiReference.EMPTY_ARRAY;

    List<PsiReference> res = null;
    
    Pattern pattern = getDelimitersPattern(mavenProject);

    Matcher matcher = pattern.matcher(text);
    
    int groupCount = matcher.groupCount();
    
    while (matcher.find()) {
      String propertyName = null;
      int from = 0;
      
      for (int i = 0; i < groupCount; i++) {
        propertyName = matcher.group(i + 1);
        if (propertyName != null) {
          from = matcher.start(i + 1);
          break;
        }
      }

      assert propertyName != null;

      if (res == null) {
        res = new ArrayList<>();
      }

      TextRange range = TextRange.from(from, propertyName.length());

      res.add(new MavenFilteredPropertyPsiReference(mavenProject, element, propertyName, range));
    }

    return res == null ? PsiReference.EMPTY_ARRAY : res.toArray(new PsiReference[res.size()]);
  }
}
