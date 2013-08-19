package com.intellij.tasks.generic.assembla;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tasks.TaskRepositoryType;
import com.intellij.tasks.generic.*;
import com.intellij.util.xmlb.annotations.Tag;

/**
 * User: evgeny.zakrevsky
 * Date: 10/26/12
 */
// TODO: move to new Generic Repository API
@Tag("Assembla")
public class AssemblaRepository extends GenericRepository {

  private static final String TASK_REGEX =
    "<ticket>.*?<number type=\"integer\">({id}.*?)</number>.*?<summary>({summary}.*?)</summary>.*?</ticket>";

  @SuppressWarnings({"UnusedDeclaration"})
  public AssemblaRepository() {
  }

  public AssemblaRepository(final TaskRepositoryType type) {
    super(type);
    setUseHttpAuthentication(true);
    setUrl("http://www.assembla.com/");
  }

  public AssemblaRepository(final AssemblaRepository other) {
    super(other);
  }

  @Override
  public AssemblaRepository clone() {
    return new AssemblaRepository(this);
  }

  @Override
  public void resetToDefaults() {
    super.resetToDefaults();
    setTasksListUrl("http://www.assembla.com/tickets.xml");
    setResponseType(ResponseType.TEXT);
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

  @Override
  public ResponseHandler getTextResponseHandlerDefault() {
    RegExResponseHandler regexHandler = new RegExResponseHandler(this);
    regexHandler.setTaskRegex(TASK_REGEX);
    return regexHandler;
  }
}
