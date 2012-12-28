package com.intellij.tasks.generic.assembla;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tasks.TaskRepositoryType;
import com.intellij.tasks.generic.GenericRepository;
import com.intellij.tasks.generic.GenericRepositoryEditor;
import com.intellij.tasks.generic.ResponseType;
import com.intellij.tasks.generic.TemplateVariable;
import com.intellij.util.xmlb.annotations.Tag;

import java.util.List;

/**
 * User: evgeny.zakrevsky
 * Date: 10/26/12
 */
@Tag("Assembla")
public class AssemblaRepository extends GenericRepository {

  @SuppressWarnings({"UnusedDeclaration"})
  public AssemblaRepository() {
  }

  public AssemblaRepository(final TaskRepositoryType type) {
    super(type);
    myUseHttpAuthentication = true;
  }

  public AssemblaRepository(final GenericRepository other) {
    super(other);
  }

  @Override
  public AssemblaRepository clone() {
    return new AssemblaRepository(this);
  }

  @Override
  protected List<TemplateVariable> getTemplateVariablesDefault() {
    return super.getTemplateVariablesDefault();
  }

  @Override
  protected ResponseType getResponseTypeDefault() {
    return ResponseType.XML;
  }

  @Override
  protected String getTasksListMethodTypeDefault() {
    return GenericRepositoryEditor.GET;
  }

  @Override
  protected String getLoginMethodTypeDefault() {
    return GenericRepositoryEditor.POST;
  }

  @Override
  protected String getLoginURLDefault() {
    return "";
  }

  @Override
  protected String getTaskPatternDefault() {
    return "<ticket>.*?<number type=\"integer\">({id}.*?)</number>.*?<summary>({summary}.*?)</summary>.*?</ticket>";
  }

  @Override
  protected String getTasksListURLDefault() {
    return "http://www.assembla.com/tickets.xml";
  }

  @Override
  public String getPresentableName() {
    return StringUtil.isEmpty(getUsername()) ? "<undefined>" : getUsername() + "'s tickets";
  }

  @Override
  public boolean isConfigured() {
    return super.isConfigured() && StringUtil.isNotEmpty(getUsername()) && StringUtil.isNotEmpty(getPassword());
  }

  @Override
  protected int getFeatures() {
    return BASIC_HTTP_AUTHORIZATION;
  }
}
