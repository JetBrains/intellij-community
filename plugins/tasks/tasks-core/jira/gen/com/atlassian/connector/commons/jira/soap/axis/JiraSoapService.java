/**
 * JiraSoapService.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis 1.4 Apr 22, 2006 (06:55:48 PDT) WSDL2Java emitter.
 */

package com.atlassian.connector.commons.jira.soap.axis;

public interface JiraSoapService extends java.rmi.Remote {
	public RemoteComment getComment(String in0, long in1)
			throws java.rmi.RemoteException, RemoteException;

	public RemoteGroup createGroup(String in0, String in1,
                                       RemoteUser in2) throws java.rmi.RemoteException,
			RemotePermissionException,
			RemoteValidationException,
			RemoteAuthenticationException,
			RemoteException;

	public RemoteSecurityLevel getSecurityLevel(String in0,
                                                    String in1) throws java.rmi.RemoteException,
			RemotePermissionException,
			RemoteException;

	public RemoteServerInfo getServerInfo(String in0)
			throws java.rmi.RemoteException;

	public RemoteGroup getGroup(String in0, String in1) throws
			java.rmi.RemoteException, RemotePermissionException,
			RemoteValidationException,
			RemoteAuthenticationException,
			RemoteException;

	public RemoteUser createUser(String in0, String in1,
                                     String in2, String in3, String in4) throws java.rmi.RemoteException,
			RemotePermissionException,
			RemoteValidationException,
			RemoteAuthenticationException,
			RemoteException;

	public RemoteUser getUser(String in0, String in1) throws
			java.rmi.RemoteException, RemotePermissionException,
			RemoteAuthenticationException;

	public String login(String in0, String in1) throws java.rmi.RemoteException,
			RemoteAuthenticationException,
			RemoteException;

	public RemoteIssue getIssue(String in0, String in1) throws
			java.rmi.RemoteException, RemotePermissionException,
			RemoteAuthenticationException,
			RemoteException;

	public RemoteIssue createIssue(String in0,
                                       RemoteIssue in1) throws java.rmi.RemoteException,
			RemotePermissionException,
			RemoteValidationException,
			RemoteAuthenticationException,
			RemoteException;

	public RemoteNamedObject[] getAvailableActions(String in0,
                                                       String in1) throws java.rmi.RemoteException, RemoteException;

	public RemoteIssue updateIssue(String in0, String in1,
                                       RemoteFieldValue[] in2)
			throws java.rmi.RemoteException, RemoteException;

	public RemoteConfiguration getConfiguration(String in0) throws
			java.rmi.RemoteException, RemotePermissionException,
			RemoteAuthenticationException,
			RemoteException;

	public RemoteComponent[] getComponents(String in0,
                                               String in1) throws java.rmi.RemoteException,
			RemotePermissionException,
			RemoteAuthenticationException,
			RemoteException;

	public RemoteProject updateProject(String in0,
                                           RemoteProject in1) throws java.rmi.RemoteException,
			RemotePermissionException,
			RemoteValidationException,
			RemoteAuthenticationException,
			RemoteException;

	public RemoteProject getProjectByKey(String in0, String in1)
			throws java.rmi.RemoteException, RemotePermissionException,
			RemoteAuthenticationException,
			RemoteException;

	public RemotePriority[] getPriorities(String in0) throws
			java.rmi.RemoteException, RemotePermissionException,
			RemoteAuthenticationException;

	public RemoteResolution[] getResolutions(String in0) throws
			java.rmi.RemoteException, RemotePermissionException,
			RemoteAuthenticationException;

	public RemoteIssueType[] getIssueTypes(String in0) throws
			java.rmi.RemoteException, RemotePermissionException,
			RemoteAuthenticationException;

	public RemoteStatus[] getStatuses(String in0) throws
			java.rmi.RemoteException, RemotePermissionException,
			RemoteAuthenticationException;

	public RemoteIssueType[] getSubTaskIssueTypes(String in0) throws
			java.rmi.RemoteException, RemotePermissionException,
			RemoteAuthenticationException;

	public RemoteProjectRole[] getProjectRoles(String in0)
			throws java.rmi.RemoteException, RemoteException;

	public RemoteProjectRole getProjectRole(String in0, long in1)
			throws java.rmi.RemoteException, RemoteException;

	public RemoteProjectRoleActors getProjectRoleActors(String in0,
                                                            RemoteProjectRole in1,
                                                            RemoteProject in2)
			throws java.rmi.RemoteException, RemoteException;

	public RemoteRoleActors getDefaultRoleActors(String in0,
                                                     RemoteProjectRole in1)
			throws java.rmi.RemoteException, RemoteException;

	public void removeAllRoleActorsByNameAndType(String in0, String in1, String in2)
			throws java.rmi.RemoteException, RemoteException;

	public void removeAllRoleActorsByProject(String in0, RemoteProject in1)
			throws java.rmi.RemoteException, RemoteException;

	public void deleteProjectRole(String in0, RemoteProjectRole in1,
                                      boolean in2) throws java.rmi.RemoteException, RemoteException;

	public void updateProjectRole(String in0, RemoteProjectRole in1)
			throws java.rmi.RemoteException, RemoteException;

	public RemoteProjectRole createProjectRole(String in0,
                                                   RemoteProjectRole in1)
			throws java.rmi.RemoteException, RemoteException;

	public boolean isProjectRoleNameUnique(String in0, String in1)
			throws java.rmi.RemoteException, RemoteException;

	public void addActorsToProjectRole(String in0, String[] in1,
                                           RemoteProjectRole in2,
                                           RemoteProject in3, String in4)
			throws java.rmi.RemoteException, RemoteException;

	public void removeActorsFromProjectRole(String in0, String[] in1,
                                                RemoteProjectRole in2,
                                                RemoteProject in3, String in4)
			throws java.rmi.RemoteException, RemoteException;

	public void addDefaultActorsToProjectRole(String in0, String[] in1,
                                                  RemoteProjectRole in2, String in3)
			throws java.rmi.RemoteException, RemoteException;

	public void removeDefaultActorsFromProjectRole(String in0, String[] in1,
                                                       RemoteProjectRole in2, String in3)
			throws java.rmi.RemoteException, RemoteException;

	public RemoteScheme[] getAssociatedNotificationSchemes(String in0,
                                                               RemoteProjectRole in1)
			throws java.rmi.RemoteException, RemoteException;

	public RemoteScheme[] getAssociatedPermissionSchemes(String in0,
                                                             RemoteProjectRole in1)
			throws java.rmi.RemoteException, RemoteException;

	public RemoteField[] getCustomFields(String in0)
			throws java.rmi.RemoteException, RemoteException;

	public RemoteComment[] getComments(String in0, String in1)
			throws java.rmi.RemoteException, RemotePermissionException,
			RemoteAuthenticationException,
			RemoteException;

	public RemoteFilter[] getFavouriteFilters(String in0) throws
			java.rmi.RemoteException, RemotePermissionException,
			RemoteAuthenticationException,
			RemoteException;

	public void archiveVersion(String in0, String in1, String in2, boolean in3)
			throws java.rmi.RemoteException, RemoteException;

	public RemoteVersion[] getVersions(String in0, String in1)
			throws java.rmi.RemoteException, RemotePermissionException,
			RemoteAuthenticationException,
			RemoteException;

	public RemoteProject createProject(String in0, String in1,
                                           String in2, String in3, String in4, String in5,
                                           RemotePermissionScheme in6,
                                           RemoteScheme in7,
                                           RemoteScheme in8) throws java.rmi.RemoteException,
			RemotePermissionException,
			RemoteValidationException,
			RemoteAuthenticationException,
			RemoteException;

	public void addComment(String in0, String in1,
                               RemoteComment in2) throws java.rmi.RemoteException,
			RemotePermissionException,
			RemoteAuthenticationException,
			RemoteException;

	public RemoteField[] getFieldsForEdit(String in0, String in1)
			throws java.rmi.RemoteException, RemoteException;

	public RemoteIssueType[] getIssueTypesForProject(String in0,
                                                         String in1) throws java.rmi.RemoteException,
			RemotePermissionException,
			RemoteAuthenticationException;

	public RemoteIssueType[] getSubTaskIssueTypesForProject(String in0,
                                                                String in1) throws java.rmi.RemoteException,
			RemotePermissionException,
			RemoteAuthenticationException;

	public void addUserToGroup(String in0, RemoteGroup in1,
                                   RemoteUser in2) throws java.rmi.RemoteException,
			RemotePermissionException,
			RemoteValidationException,
			RemoteAuthenticationException,
			RemoteException;

	public void removeUserFromGroup(String in0, RemoteGroup in1,
                                        RemoteUser in2) throws java.rmi.RemoteException,
			RemotePermissionException,
			RemoteValidationException,
			RemoteAuthenticationException,
			RemoteException;

	public boolean logout(String in0) throws java.rmi.RemoteException;

	public RemoteProject getProjectById(String in0, long in1) throws
			java.rmi.RemoteException, RemotePermissionException,
			RemoteAuthenticationException,
			RemoteException;

	public RemoteProject getProjectWithSchemesById(String in0, long in1)
			throws java.rmi.RemoteException, RemoteException;

	public void deleteProject(String in0, String in1) throws java.rmi.RemoteException,
			RemotePermissionException,
			RemoteAuthenticationException,
			RemoteException;

	public void releaseVersion(String in0, String in1,
                                   RemoteVersion in2)
			throws java.rmi.RemoteException, RemoteException;

	public RemoteSecurityLevel[] getSecurityLevels(String in0,
                                                       String in1) throws java.rmi.RemoteException,
			RemotePermissionException,
			RemoteException;

	public void deleteIssue(String in0, String in1) throws java.rmi.RemoteException,
			RemotePermissionException,
			RemoteAuthenticationException,
			RemoteException;

	public RemoteIssue createIssueWithSecurityLevel(String in0,
                                                        RemoteIssue in1, long in2) throws java.rmi.RemoteException,
			RemotePermissionException,
			RemoteValidationException,
			RemoteAuthenticationException,
			RemoteException;

	public boolean addAttachmentsToIssue(String in0, String in1, String[] in2, byte[][] in3)
			throws java.rmi.RemoteException, RemotePermissionException,
			RemoteValidationException,
			RemoteAuthenticationException,
			RemoteException;

	public RemoteAttachment[] getAttachmentsFromIssue(String in0,
                                                          String in1) throws java.rmi.RemoteException,
			RemotePermissionException,
			RemoteValidationException,
			RemoteAuthenticationException,
			RemoteException;

	public boolean hasPermissionToEditComment(String in0,
                                                  RemoteComment in1)
			throws java.rmi.RemoteException, RemoteException;

	public RemoteComment editComment(String in0,
                                         RemoteComment in1)
			throws java.rmi.RemoteException, RemoteException;

	public RemoteField[] getFieldsForAction(String in0,
                                                String in1, String in2)
			throws java.rmi.RemoteException, RemoteException;

	public RemoteIssue progressWorkflowAction(String in0,
                                                  String in1, String in2, RemoteFieldValue[] in3)
			throws java.rmi.RemoteException, RemoteException;

	public RemoteIssue getIssueById(String in0, String in1)
			throws java.rmi.RemoteException, RemotePermissionException,
			RemoteAuthenticationException,
			RemoteException;

	public RemoteWorklog addWorklogWithNewRemainingEstimate(String in0,
                                                                String in1, RemoteWorklog in2, String in3) throws
			java.rmi.RemoteException, RemotePermissionException,
			RemoteValidationException,
			RemoteException;

	public RemoteWorklog addWorklogAndAutoAdjustRemainingEstimate(
          String in0, String in1, RemoteWorklog in2) throws
			java.rmi.RemoteException, RemotePermissionException,
			RemoteValidationException,
			RemoteException;

	public RemoteWorklog addWorklogAndRetainRemainingEstimate(String in0,
                                                                  String in1, RemoteWorklog in2) throws java.rmi.RemoteException,
			RemotePermissionException,
			RemoteValidationException,
			RemoteException;

	public void deleteWorklogWithNewRemainingEstimate(String in0, String in1, String in2) throws
			java.rmi.RemoteException, RemotePermissionException,
			RemoteValidationException,
			RemoteException;

	public void deleteWorklogAndAutoAdjustRemainingEstimate(String in0, String in1) throws
			java.rmi.RemoteException, RemotePermissionException,
			RemoteValidationException,
			RemoteException;

	public void deleteWorklogAndRetainRemainingEstimate(String in0, String in1) throws
			java.rmi.RemoteException, RemotePermissionException,
			RemoteValidationException,
			RemoteException;

	public void updateWorklogWithNewRemainingEstimate(String in0,
                                                          RemoteWorklog in1, String in2) throws java.rmi.RemoteException,
			RemotePermissionException,
			RemoteValidationException,
			RemoteException;

	public void updateWorklogAndAutoAdjustRemainingEstimate(String in0,
                                                                RemoteWorklog in1) throws java.rmi.RemoteException,
			RemotePermissionException,
			RemoteValidationException,
			RemoteException;

	public void updateWorklogAndRetainRemainingEstimate(String in0,
                                                            RemoteWorklog in1) throws java.rmi.RemoteException,
			RemotePermissionException,
			RemoteValidationException,
			RemoteException;

	public RemoteWorklog[] getWorklogs(String in0, String in1)
			throws java.rmi.RemoteException, RemotePermissionException,
			RemoteValidationException,
			RemoteException;

	public boolean hasPermissionToCreateWorklog(String in0, String in1) throws java.rmi.RemoteException,
			RemoteValidationException,
			RemoteException;

	public boolean hasPermissionToDeleteWorklog(String in0, String in1) throws java.rmi.RemoteException,
			RemoteValidationException,
			RemoteException;

	public boolean hasPermissionToUpdateWorklog(String in0, String in1) throws java.rmi.RemoteException,
			RemoteValidationException,
			RemoteException;

	public RemoteScheme[] getNotificationSchemes(String in0) throws
			java.rmi.RemoteException, RemotePermissionException,
			RemoteAuthenticationException,
			RemoteException;

	public RemotePermissionScheme[] getPermissionSchemes(String in0) throws
			java.rmi.RemoteException, RemotePermissionException,
			RemoteAuthenticationException,
			RemoteException;

	public RemotePermissionScheme createPermissionScheme(String in0,
                                                             String in1, String in2) throws java.rmi.RemoteException,
			RemotePermissionException,
			RemoteValidationException,
			RemoteAuthenticationException,
			RemoteException;

	public void deletePermissionScheme(String in0, String in1) throws java.rmi.RemoteException,
			RemotePermissionException,
			RemoteValidationException,
			RemoteAuthenticationException,
			RemoteException;

	public RemotePermissionScheme addPermissionTo(String in0,
                                                      RemotePermissionScheme in1,
                                                      RemotePermission in2,
                                                      RemoteEntity in3) throws java.rmi.RemoteException,
			RemotePermissionException,
			RemoteValidationException,
			RemoteAuthenticationException,
			RemoteException;

	public RemotePermissionScheme deletePermissionFrom(String in0,
                                                           RemotePermissionScheme in1,
                                                           RemotePermission in2,
                                                           RemoteEntity in3) throws java.rmi.RemoteException,
			RemotePermissionException,
			RemoteValidationException,
			RemoteAuthenticationException,
			RemoteException;

	public RemotePermission[] getAllPermissions(String in0) throws
			java.rmi.RemoteException, RemotePermissionException,
			RemoteAuthenticationException,
			RemoteException;

	public long getIssueCountForFilter(String in0, String in1)
			throws java.rmi.RemoteException, RemoteException;

	public RemoteIssue[] getIssuesFromTextSearch(String in0,
                                                     String in1) throws java.rmi.RemoteException, RemoteException;

	public RemoteIssue[] getIssuesFromTextSearchWithProject(String in0,
                                                                String[] in1, String in2, int in3)
			throws java.rmi.RemoteException, RemoteException;

	public void deleteUser(String in0, String in1) throws java.rmi.RemoteException,
			RemotePermissionException,
			RemoteValidationException,
			RemoteAuthenticationException,
			RemoteException;

	public RemoteGroup updateGroup(String in0,
                                       RemoteGroup in1) throws java.rmi.RemoteException,
			RemotePermissionException,
			RemoteValidationException,
			RemoteAuthenticationException,
			RemoteException;

	public void deleteGroup(String in0, String in1, String in2) throws java.rmi.RemoteException,
			RemotePermissionException,
			RemoteValidationException,
			RemoteAuthenticationException,
			RemoteException;

	public void refreshCustomFields(String in0)
			throws java.rmi.RemoteException, RemoteException;

	public RemoteProject[] getProjectsNoSchemes(String in0) throws
			java.rmi.RemoteException, RemotePermissionException,
			RemoteAuthenticationException,
			RemoteException;

	public RemoteVersion addVersion(String in0, String in1,
                                        RemoteVersion in2)
			throws java.rmi.RemoteException, RemoteException;

	public RemoteFilter[] getSavedFilters(String in0) throws
			java.rmi.RemoteException, RemotePermissionException,
			RemoteAuthenticationException,
			RemoteException;

	public boolean addBase64EncodedAttachmentsToIssue(String in0, String in1, String[] in2,
                                                          String[] in3) throws java.rmi.RemoteException,
			RemotePermissionException,
			RemoteValidationException,
			RemoteAuthenticationException,
			RemoteException;

	public RemoteProject createProjectFromObject(String in0,
                                                     RemoteProject in1) throws java.rmi.RemoteException,
			RemotePermissionException,
			RemoteValidationException,
			RemoteAuthenticationException,
			RemoteException;

	public RemoteScheme[] getSecuritySchemes(String in0) throws
			java.rmi.RemoteException, RemotePermissionException,
			RemoteAuthenticationException,
			RemoteException;

	public RemoteIssue[] getIssuesFromFilter(String in0,
                                                 String in1) throws java.rmi.RemoteException, RemoteException;

	public RemoteIssue[] getIssuesFromFilterWithLimit(String in0,
                                                          String in1, int in2, int in3)
			throws java.rmi.RemoteException, RemoteException;

	public RemoteIssue[] getIssuesFromTextSearchWithLimit(String in0,
                                                              String in1, int in2, int in3)
			throws java.rmi.RemoteException, RemoteException;
}
