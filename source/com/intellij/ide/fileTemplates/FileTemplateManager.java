package com.intellij.ide.fileTemplates;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

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

  @NotNull public abstract FileTemplate[] getAllTemplates();

  public abstract FileTemplate getTemplate(@NotNull @NonNls String templateName);

  @NotNull public abstract Properties getDefaultProperties();

  /**
   * Creates a new template with specified name.
   * @param name
   * @return created template
   */
  @NotNull public abstract FileTemplate addTemplate(@NotNull @NonNls String name, @NotNull @NonNls String extension);

  public abstract void removeTemplate(@NotNull FileTemplate template, boolean fromDiskOnly);
  public abstract void removeInternal(@NotNull FileTemplate template);

  @NotNull public abstract Collection<String> getRecentNames();

  public abstract void addRecentName(@NotNull @NonNls String name);

  public abstract void saveAll();

  public abstract FileTemplate getInternalTemplate(@NotNull @NonNls String templateName);
  @NotNull public abstract FileTemplate[] getInternalTemplates();

  public abstract FileTemplate getJ2eeTemplate(@NotNull @NonNls String templateName);
  public abstract FileTemplate getCodeTemplate(@NotNull @NonNls String templateName);

  @NotNull public abstract FileTemplate[] getAllPatterns();

  public abstract FileTemplate addPattern(@NotNull @NonNls String name, @NotNull @NonNls String extension);

  @NotNull public abstract FileTemplate[] getAllCodeTemplates();
  @NotNull public abstract FileTemplate[] getAllJ2eeTemplates();

  @NotNull public abstract FileTemplate addCodeTemplate(@NotNull @NonNls String name, @NotNull @NonNls String extension);
  @NotNull public abstract FileTemplate addJ2eeTemplate(@NotNull @NonNls String name, @NotNull @NonNls String extension);

  public abstract void removePattern(@NotNull FileTemplate template, boolean fromDiskOnly);
  public abstract void removeCodeTemplate(@NotNull FileTemplate template, boolean fromDiskOnly);
  public abstract void removeJ2eeTemplate(@NotNull FileTemplate template, boolean fromDiskOnly);

  @NotNull public abstract String internalTemplateToSubject(@NotNull @NonNls String templateName);

  @NotNull public abstract String localizeInternalTemplateName(final FileTemplate template);

  public abstract FileTemplate getPattern(@NotNull @NonNls String name);

  public abstract FileTemplate getDefaultTemplate(@NotNull @NonNls String name);
}
