package com.intellij.ide.fileTemplates;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NonNls;

import java.util.Collection;
import java.util.Properties;

/**
 * @author MYakovlev
 * Date: Jul 24, 2002
 */
public abstract class FileTemplateManager{
  public static final int RECENT_TEMPLATES_SIZE = 25;

  @NonNls public static final String TEMPLATE_CATCH_BODY = "Catch Statement Body.java";
  @NonNls public static final String TEMPLATE_IMPLEMENTED_METHOD_BODY = "Implemented Method Body.java";
  @NonNls public static final String TEMPLATE_OVERRIDDEN_METHOD_BODY = "Overridden Method Body.java";
  @NonNls public static final String TEMPLATE_FROM_USAGE_METHOD_BODY = "New Method Body.java";
  @NonNls public static final String TEMPLATE_I18NIZED_EXPRESSION = "I18nized Expression.java";
  @NonNls public static final String TEMPLATE_I18NIZED_CONCATENATION = "I18nized Concatenation.java";
  @NonNls public static final String TEMPLATE_I18NIZED_JSP_EXPRESSION = "I18nized JSP Expression.jsp";

  @NonNls public static final String INTERNAL_CLASS_TEMPLATE_NAME = "Class";
  @NonNls public static final String INTERNAL_INTERFACE_TEMPLATE_NAME = "Interface";
  @NonNls public static final String INTERNAL_ANNOTATION_TYPE_TEMPLATE_NAME = "AnnotationType";
  @NonNls public static final String INTERNAL_ENUM_TEMPLATE_NAME = "Enum";
  @NonNls public static final String INTERNAL_HTML_TEMPLATE_NAME = "Html";
  @NonNls public static final String INTERNAL_XHTML_TEMPLATE_NAME = "Xhtml";
  @NonNls public static final String FILE_HEADER_TEMPLATE_NAME = "File Header";

  public static FileTemplateManager getInstance(){
    return ApplicationManager.getApplication().getComponent(FileTemplateManager.class);
  }

  public abstract FileTemplate[] getAllTemplates();

  public abstract FileTemplate getTemplate(String templateName);

  public abstract Properties getDefaultProperties();

  /**
   * Creates a new template with specified name.
   * @param name
   * @return created template
   */
  public abstract FileTemplate addTemplate(String name, String extension);

  public abstract void removeTemplate(FileTemplate template, boolean fromDiskOnly);
  public abstract void removeInternal(FileTemplate template);

  public abstract Collection<String> getRecentNames();

  public abstract void addRecentName(String name);

  public abstract void saveAll();

  public abstract FileTemplate getInternalTemplate(String templateName);
  public abstract FileTemplate[] getInternalTemplates();

  public abstract FileTemplate getJ2eeTemplate(String templateName);
  public abstract FileTemplate getCodeTemplate(String templateName);

  public abstract FileTemplate[] getAllPatterns();

  public abstract FileTemplate addPattern(String name, String extension);

  public abstract FileTemplate[] getAllCodeTemplates();
  public abstract FileTemplate[] getAllJ2eeTemplates();

  public abstract FileTemplate addCodeTemplate(String name, String extension);
  public abstract FileTemplate addJ2eeTemplate(String name, String extension);

  public abstract void removePattern(FileTemplate template, boolean fromDiskOnly);
  public abstract void removeCodeTemplate(FileTemplate template, boolean fromDiskOnly);
  public abstract void removeJ2eeTemplate(FileTemplate template, boolean fromDiskOnly);

  public abstract String internalTemplateToSubject(String templateName);

  public abstract String localizeInternalTemplateName(final FileTemplate template);

  public abstract FileTemplate getPattern(String name);

  public abstract FileTemplate getDefaultTemplate(String name);
}
