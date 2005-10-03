/**
 * created at Jan 3, 2002
 * @author Jeka
 */
package com.intellij.compiler;

import com.intellij.compiler.impl.ExcludeEntryDescription;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.util.Options;
import com.intellij.CommonBundle;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class CompilerConfiguration implements JDOMExternalizable, ProjectComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.CompilerConfiguration");

  public static final @NonNls String TESTS_EXTERNAL_COMPILER_HOME_PROPERTY_NAME = "tests.external.compiler.home";
  public static final int DEPENDENCY_FORMAT_VERSION = 41;
  public static final @NonNls String JAVAC = "Javac";
  public static final @NonNls String JIKES = "Jikes";

  public String DEFAULT_COMPILER = JAVAC;
  public boolean CLEAR_OUTPUT_DIRECTORY = false;

  // exclude from compile
  private List myExcludeEntryDescriptions = new ArrayList();
  // extensions of the files considered as resource files
  private List myRegexpResourcePaterns = new ArrayList(getDefaultRegexpPatterns());
  // extensions of the files considered as resource files. If present, Overrides patterns in old regexp format stored in myRegexpResourcePaterns
  private List<String> myWildcardPatterns = new ArrayList<String>();
  private List<Matcher> myWildcardCompiledPatterns = new ArrayList<Matcher>();
  private boolean myWildcardPatternsInitialized = false;
  private final Project myProject;


  public int DEPLOY_AFTER_MAKE = Options.SHOW_DIALOG;

  {
    loadDefaultWildcardPatterns();
  }

  public CompilerConfiguration(Project project) {
    myProject = project;
  }

  public void loadDefaultWildcardPatterns() {
    if (!myWildcardPatterns.isEmpty()) {
      removeWildcardPatterns();
    }
    addWildcardResourcePattern("?*.properties");
    addWildcardResourcePattern("?*.xml");
    addWildcardResourcePattern("?*.gif");
    addWildcardResourcePattern("?*.png");
    addWildcardResourcePattern("?*.jpeg");
    addWildcardResourcePattern("?*.jpg");
    addWildcardResourcePattern("?*.html");
    addWildcardResourcePattern("?*.dtd");
    addWildcardResourcePattern("?*.tld");
  }

  private List getDefaultRegexpPatterns() {
    return Arrays.asList(compilePattern(".+\\.(properties|xml|html|dtd|tld)"), compilePattern(".+\\.(gif|png|jpeg|jpg)"));
  }

  private Pattern compilePattern(@NonNls String s) throws PatternSyntaxException {
    final Pattern pattern;
    if (SystemInfo.isFileSystemCaseSensitive) {
      pattern = Pattern.compile(s);
    }
    else {
      pattern = Pattern.compile(s, Pattern.CASE_INSENSITIVE);
    }
    return pattern;
  }

  public void disposeComponent() {
  }

  public void initComponent() { }

  public void projectClosed() {
  }

  public void projectOpened() {
  }

  public String[] getResourceFilePatterns() {
    return getWildcardPatterns();
  }

  public String[] getRegexpPatterns() {
    String[] patterns = new String[myRegexpResourcePaterns.size()];
    int index = 0;
    for (Iterator it = myRegexpResourcePaterns.iterator(); it.hasNext();) {
      patterns[index++] = ((Pattern)it.next()).pattern();
    }
    return patterns;
  }

  public String[] getWildcardPatterns() {
    return myWildcardPatterns.toArray(new String[myWildcardPatterns.size()]);
  }

  public void addResourceFilePattern(String namePattern) throws PatternSyntaxException {
    addWildcardResourcePattern(namePattern);
  }

  // need this method only for handling patterns in old regexp format
  private void addRegexpPattern(String namePattern) {
    Pattern pattern = compilePattern(namePattern);
    if (pattern != null) {
      myRegexpResourcePaterns.add(pattern);
    }
  }

  private void addWildcardResourcePattern(@NonNls final String wildcardPattern) {
    final Pattern pattern = compilePattern(convertToRegexp(wildcardPattern));
    if (pattern != null) {
      myWildcardPatterns.add(wildcardPattern);
      myWildcardCompiledPatterns.add(pattern.matcher(""));
    }
  }

  public void removeResourceFilePatterns() {
    removeWildcardPatterns();
  }

  public void removeRegexpPatterns() {
    myRegexpResourcePaterns.clear();
  }

  public void removeWildcardPatterns() {
    myWildcardPatterns.clear();
    myWildcardCompiledPatterns.clear();
  }

  private String convertToRegexp(String wildcardPattern) {
    if (isPatternNegated(wildcardPattern)) {
      wildcardPattern = wildcardPattern.substring(1);
    }
    return wildcardPattern.
      replaceAll("\\\\!", "!").
      replaceAll("\\.", "\\\\.").
      replaceAll("\\*\\?", ".+").
      replaceAll("\\?\\*", ".+").
      replaceAll("\\*", ".*").
      replaceAll("\\?", ".").
      replaceAll("(?:\\.\\*)+", ".*")  // optimization
    ;
  }

  public boolean isPatternNegated(String wildcardPattern) {
    return wildcardPattern.length() > 1 && wildcardPattern.charAt(0) == '!';
  }

  public boolean isResourceFile(String name) {
    int idx = 0;
    for (Iterator<Matcher> it = myWildcardCompiledPatterns.iterator(); it.hasNext(); idx++) {
      final Matcher matcher = it.next();
      matcher.reset(name);
      final boolean matches = matcher.matches();
      if (isPatternNegated(myWildcardPatterns.get(idx))? !matches : matches) {
        return true;
      }
    }
    return false;
  }

  public ExcludeEntryDescription[] getExcludeEntryDescriptions() {
    return (ExcludeEntryDescription[])myExcludeEntryDescriptions.toArray(new ExcludeEntryDescription[myExcludeEntryDescriptions.size()]);
  }

  public void addExcludeEntryDescription(ExcludeEntryDescription description) {
    myExcludeEntryDescriptions.add(description);
  }

  public void removeAllExcludeEntryDescriptions() {
    myExcludeEntryDescriptions.clear();
  }

  // property names
  private static final @NonNls String EXCLUDE_FROM_COMPILE = "excludeFromCompile";
  private static final @NonNls String RESOURCE_EXTENSIONS = "resourceExtensions";
  private static final @NonNls String WILDCARD_RESOURCE_PATTERNS = "wildcardResourcePatterns";
  private static final @NonNls String ENTRY = "entry";
  private static final @NonNls String NAME = "name";
  private static final @NonNls String FILE = "file";
  private static final @NonNls String DIRECTORY = "directory";
  private static final @NonNls String URL = "url";
  private static final @NonNls String INCLUDE_SUBDIRECTORIES = "includeSubdirectories";

  @SuppressWarnings({"HardCodedStringLiteral"})
  public void readExternal(Element parentNode) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, parentNode);

    Element node = parentNode.getChild(EXCLUDE_FROM_COMPILE);
    if (node != null) {
      for (Iterator i = node.getChildren().iterator(); i.hasNext();) {
        Element element = (Element)i.next();
        String url = element.getAttributeValue(URL);
        if (url == null) continue;
        if(FILE.equals(element.getName())) {
          ExcludeEntryDescription excludeEntryDescription = new ExcludeEntryDescription(url, false, true);
          myExcludeEntryDescriptions.add(excludeEntryDescription);
        }
        if(DIRECTORY.equals(element.getName())) {
          boolean includeSubdirectories = true;
          if("false".equals(element.getAttributeValue(INCLUDE_SUBDIRECTORIES))) {
            includeSubdirectories = false;
          }
          ExcludeEntryDescription excludeEntryDescription = new ExcludeEntryDescription(url, includeSubdirectories, false);
          myExcludeEntryDescriptions.add(excludeEntryDescription);
        }
      }
    }

    removeRegexpPatterns();
    node = parentNode.getChild(RESOURCE_EXTENSIONS);
    if (node != null) {
      for (Iterator iterator = node.getChildren(ENTRY).iterator(); iterator.hasNext();) {
        Element element = (Element)iterator.next();
        String pattern = element.getAttributeValue(NAME);
        if (pattern != null && !"".equals(pattern)) {
          addRegexpPattern(pattern);
        }
      }
    }

    removeWildcardPatterns();
    node = parentNode.getChild(WILDCARD_RESOURCE_PATTERNS);
    if (node != null) {
      myWildcardPatternsInitialized = true;
      for (Iterator iterator = node.getChildren(ENTRY).iterator(); iterator.hasNext();) {
        final Element element = (Element)iterator.next();
        String pattern = element.getAttributeValue(NAME);
        if (pattern != null && !"".equals(pattern)) {
          addWildcardResourcePattern(pattern);
        }
      }
    }

  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public void writeExternal(Element parentNode) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, parentNode);

    if(myExcludeEntryDescriptions.size() > 0) {
      Element newChild = new Element(EXCLUDE_FROM_COMPILE);
      for (Iterator it = myExcludeEntryDescriptions.iterator(); it.hasNext();) {
        ExcludeEntryDescription description = (ExcludeEntryDescription)it.next();
        if(description.isFile()) {
          Element entry = new Element(FILE);
          entry.setAttribute(URL, description.getUrl());
          newChild.addContent(entry);
        }
        else {
          Element entry = new Element(DIRECTORY);
          entry.setAttribute(URL, description.getUrl());
          entry.setAttribute(INCLUDE_SUBDIRECTORIES, description.isIncludeSubdirectories() ? "true" : "false");
          newChild.addContent(entry);
        }
      }
      parentNode.addContent(newChild);
    }

    String[] patterns = getRegexpPatterns();
    final Element newChild = new Element(RESOURCE_EXTENSIONS);
    for (int idx = 0; idx < patterns.length; idx++) {
      final String pattern = patterns[idx];
      final Element entry = new Element(ENTRY);
      entry.setAttribute(NAME, pattern);
      newChild.addContent(entry);
    }
    parentNode.addContent(newChild);

    if (myWildcardPatternsInitialized || !myWildcardPatterns.isEmpty()) {
      final Element wildcardPatterns = new Element(WILDCARD_RESOURCE_PATTERNS);
      for (Iterator<String> it = myWildcardPatterns.iterator(); it.hasNext();) {
        final String wildcardPattern = it.next();
        final Element entry = new Element(ENTRY);
        entry.setAttribute(NAME, wildcardPattern);
        wildcardPatterns.addContent(entry);
      }
      parentNode.addContent(wildcardPatterns);
    }
  }

  public static CompilerConfiguration getInstance(Project project) {
    return project.getComponent(CompilerConfiguration.class);
  }

  public boolean isExcludedFromCompilation(VirtualFile virtualFile) {
    for (Iterator it = myExcludeEntryDescriptions.iterator(); it.hasNext();) {
      ExcludeEntryDescription entryDescription = (ExcludeEntryDescription)it.next();
      VirtualFile descriptionFile = entryDescription.getVirtualFile();
      if (descriptionFile == null) {
        continue;
      }
      if(entryDescription.isFile()) {
        if(descriptionFile.equals(virtualFile)) {
          return true;
        }
      }
      else {
        if(entryDescription.isIncludeSubdirectories()) {
          if (VfsUtil.isAncestor(descriptionFile, virtualFile, false)) {
            return true;
          }
        }
        else {
          if (virtualFile.isDirectory()) {
            continue;
          }
          if (descriptionFile.equals(virtualFile.getParent())) {
            return true;
          }
        }
      }
    }
    return false;
  }

  public String getComponentName() {
    return "CompilerConfiguration";
  }

  public String getDefaultCompiler() {
    return DEFAULT_COMPILER;
  }

  public void setDefaultCompiler(String defaultCompiler) {
    LOG.assertTrue(defaultCompiler.equals(JAVAC) || defaultCompiler.equals(JIKES), "Unsupported compiler");
    this.DEFAULT_COMPILER = defaultCompiler;
  }

  public boolean isClearOutputDirectory() {
    return CLEAR_OUTPUT_DIRECTORY;
  }

  public void setClearOutputDirectory(boolean value) {
    this.CLEAR_OUTPUT_DIRECTORY = value;
  }

  public void convertPatterns() {
    if (!needPatternConversion()) {
      return;
    }
    try {
      boolean ok = doConvertPatterns();
      if (!ok) {
        final String initialPatternString = patternsToString(getRegexpPatterns());
        final String message = CompilerBundle.message(
          "message.resource.patterns.format.changed",
          ApplicationNamesInfo.getInstance().getProductName(),
          initialPatternString,
          CommonBundle.getOkButtonText(),
          CommonBundle.getCancelButtonText()
        );
        final String wildcardPatterns = Messages.showInputDialog(
          myProject, message, CompilerBundle.message("pattern.conversion.dialog.title"), Messages.getWarningIcon(), initialPatternString, new InputValidator() {
          public boolean checkInput(String inputString) {
            return true;
          }
          public boolean canClose(String inputString) {
            final StringTokenizer tokenizer = new StringTokenizer(inputString, ";", false);
            StringBuffer malformedPatterns = new StringBuffer();

            while (tokenizer.hasMoreTokens()) {
              String pattern = tokenizer.nextToken();
              try {
                addWildcardResourcePattern(pattern);
              }
              catch (PatternSyntaxException e) {
                malformedPatterns.append("\n\n");
                malformedPatterns.append(pattern);
                malformedPatterns.append(": ");
                malformedPatterns.append(e.getMessage());
              }
            }

            if (malformedPatterns.length() > 0) {
              Messages.showErrorDialog(CompilerBundle.message("error.bad.resource.patterns", malformedPatterns.toString()),
                                       CompilerBundle.message("bad.resource.patterns.dialog.title"));
              removeWildcardPatterns();
              return false;
            }
            return true;
          }
        });
        if (wildcardPatterns == null) { // cancel pressed
          loadDefaultWildcardPatterns();
        }
      }
    }
    finally {
      myWildcardPatternsInitialized = true;
    }
  }

  private boolean needPatternConversion() {
    return !myWildcardPatternsInitialized && !myRegexpResourcePaterns.isEmpty();
  }

  private boolean doConvertPatterns() {
    final String[] regexpPatterns = getRegexpPatterns();
    final List<String> converted = new ArrayList<String>();
    final Pattern multipleExtensionsPatternPattern = compilePattern("\\.\\+\\\\\\.\\((\\w+(?:\\|\\w+)*)\\)");
    final Pattern singleExtensionPatternPattern = compilePattern("\\.\\+\\\\\\.(\\w+)");
    for (int idx = 0; idx < regexpPatterns.length; idx++) {
      final String regexpPattern = regexpPatterns[idx];
      final Matcher multipleExtensionsMatcher = multipleExtensionsPatternPattern.matcher(regexpPattern);
      if (multipleExtensionsMatcher.matches()) {
        final StringTokenizer tokenizer = new StringTokenizer(multipleExtensionsMatcher.group(1), "|", false);
        while (tokenizer.hasMoreTokens()) {
          converted.add("?*." + tokenizer.nextToken());
        }
      }
      else {
        final Matcher singleExtensionMatcher = singleExtensionPatternPattern.matcher(regexpPattern);
        if (singleExtensionMatcher.matches()) {
          converted.add("?*." + singleExtensionMatcher.group(1));
        }
        else {
          return false;
        }
      }
    }
    for (Iterator it = converted.iterator(); it.hasNext();) {
      final String wildcardPattern = (String)it.next();
      addWildcardResourcePattern(wildcardPattern);
    }
    return true;
  }

  private static String patternsToString(final String[] patterns) {
    final StringBuffer extensionsString = new StringBuffer();
    for (int idx = 0; idx < patterns.length; idx++) {
      if (idx > 0) {
        extensionsString.append(";");
      }
      extensionsString.append(patterns[idx]);
    }
    return extensionsString.toString();
  }

}
