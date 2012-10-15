/**
 * MantisConnectPortType.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis 1.4 Apr 22, 2006 (06:55:48 PDT) WSDL2Java emitter.
 */

package com.intellij.tasks.mantis.model;

import java.math.BigInteger;
import java.rmi.Remote;
import java.rmi.RemoteException;

public interface MantisConnectPortType extends Remote {
  String mc_version() throws RemoteException;

  /**
   * Get the enumeration for statuses.
   */
  ObjectRef[] mc_enum_status(String username, String password) throws RemoteException;

  /**
   * Get the enumeration for priorities.
   */
  ObjectRef[] mc_enum_priorities(String username, String password) throws RemoteException;

  /**
   * Get the enumeration for severities.
   */
  ObjectRef[] mc_enum_severities(String username, String password) throws RemoteException;

  /**
   * Get the enumeration for reproducibilities.
   */
  ObjectRef[] mc_enum_reproducibilities(String username, String password) throws RemoteException;

  /**
   * Get the enumeration for projections.
   */
  ObjectRef[] mc_enum_projections(String username, String password) throws RemoteException;

  /**
   * Get the enumeration for ETAs.
   */
  ObjectRef[] mc_enum_etas(String username, String password) throws RemoteException;

  /**
   * Get the enumeration for resolutions.
   */
  ObjectRef[] mc_enum_resolutions(String username, String password) throws RemoteException;

  /**
   * Get the enumeration for access levels.
   */
  ObjectRef[] mc_enum_access_levels(String username, String password) throws RemoteException;

  /**
   * Get the enumeration for project statuses.
   */
  ObjectRef[] mc_enum_project_status(String username, String password) throws RemoteException;

  /**
   * Get the enumeration for project view states.
   */
  ObjectRef[] mc_enum_project_view_states(String username, String password) throws RemoteException;

  /**
   * Get the enumeration for view states.
   */
  ObjectRef[] mc_enum_view_states(String username, String password) throws RemoteException;

  /**
   * Get the enumeration for custom field types.
   */
  ObjectRef[] mc_enum_custom_field_types(String username, String password) throws RemoteException;

  /**
   * Get the enumeration for the specified enumeration type.
   */
  String mc_enum_get(String username, String password, String enumeration) throws RemoteException;

  /**
   * Check there exists an issue with the specified id.
   */
  boolean mc_issue_exists(String username, String password, BigInteger issue_id) throws RemoteException;

  /**
   * Get the issue with the specified id.
   */
  IssueData mc_issue_get(String username, String password, BigInteger issue_id) throws RemoteException;

  /**
   * Get the latest submitted issue in the specified project.
   */
  BigInteger mc_issue_get_biggest_id(String username, String password, BigInteger project_id) throws RemoteException;

  /**
   * Get the id of the issue with the specified summary.
   */
  BigInteger mc_issue_get_id_from_summary(String username, String password, String summary) throws RemoteException;

  /**
   * Submit the specified issue details.
   */
  BigInteger mc_issue_add(String username, String password, IssueData issue) throws RemoteException;

  /**
   * Update Issue method.
   */
  boolean mc_issue_update(String username, String password, BigInteger issueId, IssueData issue) throws RemoteException;

  /**
   * Sets the tags for a specified issue.
   */
  boolean mc_issue_set_tags(String username, String password, BigInteger issue_id, TagData[] tags) throws RemoteException;

  /**
   * Delete the issue with the specified id.
   */
  boolean mc_issue_delete(String username, String password, BigInteger issue_id) throws RemoteException;

  /**
   * Submit a new note.
   */
  BigInteger mc_issue_note_add(String username, String password, BigInteger issue_id, IssueNoteData note) throws RemoteException;

  /**
   * Delete the note with the specified id.
   */
  boolean mc_issue_note_delete(String username, String password, BigInteger issue_note_id) throws RemoteException;

  /**
   * Update a specific note of a specific issue.
   */
  boolean mc_issue_note_update(String username, String password, IssueNoteData note) throws RemoteException;

  /**
   * Submit a new relationship.
   */
  BigInteger mc_issue_relationship_add(String username,
                                       String password,
                                       BigInteger issue_id,
                                       RelationshipData relationship) throws RemoteException;

  /**
   * Delete the relationship for the specified issue.
   */
  boolean mc_issue_relationship_delete(String username, String password, BigInteger issue_id, BigInteger relationship_id)
    throws RemoteException;

  /**
   * Submit a new issue attachment.
   */
  BigInteger mc_issue_attachment_add(String username, String password, BigInteger issue_id, String name, String file_type, byte[] content)
    throws RemoteException;

  /**
   * Delete the issue attachment with the specified id.
   */
  boolean mc_issue_attachment_delete(String username, String password, BigInteger issue_attachment_id) throws RemoteException;

  /**
   * Get the data for the specified issue attachment.
   */
  byte[] mc_issue_attachment_get(String username, String password, BigInteger issue_attachment_id) throws RemoteException;

  /**
   * Add a new project to the tracker (must have admin privileges)
   */
  BigInteger mc_project_add(String username, String password, ProjectData project) throws RemoteException;

  /**
   * Add a new project to the tracker (must have admin privileges)
   */
  boolean mc_project_delete(String username, String password, BigInteger project_id) throws RemoteException;

  /**
   * Update a specific project to the tracker (must have admin privileges)
   */
  boolean mc_project_update(String username, String password, BigInteger project_id, ProjectData project) throws RemoteException;

  /**
   * Get the id of the project with the specified name.
   */
  BigInteger mc_project_get_id_from_name(String username, String password, String project_name) throws RemoteException;

  /**
   * Get the issues that match the specified project id and paging
   * details.
   */
  IssueData[] mc_project_get_issues(String username,
                                    String password,
                                    BigInteger project_id,
                                    BigInteger page_number,
                                    BigInteger per_page) throws RemoteException;

  /**
   * Get the issue headers that match the specified project id and
   * paging details.
   */
  IssueHeaderData[] mc_project_get_issue_headers(String username,
                                                 String password,
                                                 BigInteger project_id,
                                                 BigInteger page_number,
                                                 BigInteger per_page) throws RemoteException;

  /**
   * Get appropriate users assigned to a project by access level.
   */
  AccountData[] mc_project_get_users(String username,
                                     String password,
                                     BigInteger project_id,
                                     BigInteger access) throws RemoteException;

  /**
   * Get the list of projects that are accessible to the logged
   * in user.
   */
  ProjectData[] mc_projects_get_user_accessible(String username, String password) throws RemoteException;

  /**
   * Get the categories belonging to the specified project.
   */
  String[] mc_project_get_categories(String username, String password, BigInteger project_id) throws RemoteException;

  /**
   * Add a category of specific project.
   */
  BigInteger mc_project_add_category(String username, String password, BigInteger project_id, String p_category_name)
    throws RemoteException;

  /**
   * Delete a category of specific project.
   */
  BigInteger mc_project_delete_category(String username, String password, BigInteger project_id, String p_category_name)
    throws RemoteException;

  /**
   * Rename a category of specific project.
   */
  BigInteger mc_project_rename_category_by_name(String username,
                                                String password,
                                                BigInteger project_id,
                                                String p_category_name,
                                                String p_category_name_new,
                                                BigInteger p_assigned_to) throws RemoteException;

  /**
   * Get the versions belonging to the specified project.
   */
  ProjectVersionData[] mc_project_get_versions(String username, String password, BigInteger project_id) throws RemoteException;

  /**
   * Submit the specified version details.
   */
  BigInteger mc_project_version_add(String username, String password, ProjectVersionData version) throws RemoteException;

  /**
   * Update version method.
   */
  boolean mc_project_version_update(String username,
                                    String password,
                                    BigInteger version_id,
                                    ProjectVersionData version) throws RemoteException;

  /**
   * Delete the version with the specified id.
   */
  boolean mc_project_version_delete(String username, String password, BigInteger version_id) throws RemoteException;

  /**
   * Get the released versions that belong to the specified project.
   */
  ProjectVersionData[] mc_project_get_released_versions(String username,
                                                        String password,
                                                        BigInteger project_id) throws RemoteException;

  /**
   * Get the unreleased version that belong to the specified project.
   */
  ProjectVersionData[] mc_project_get_unreleased_versions(String username,
                                                          String password,
                                                          BigInteger project_id) throws RemoteException;

  /**
   * Get the attachments that belong to the specified project.
   */
  ProjectAttachmentData[] mc_project_get_attachments(String username,
                                                     String password,
                                                     BigInteger project_id) throws RemoteException;

  /**
   * Get the custom fields that belong to the specified project.
   */
  CustomFieldDefinitionData[] mc_project_get_custom_fields(String username,
                                                           String password,
                                                           BigInteger project_id) throws RemoteException;

  /**
   * Get the data for the specified project attachment.
   */
  byte[] mc_project_attachment_get(String username, String password, BigInteger project_attachment_id) throws RemoteException;

  /**
   * Submit a new project attachment.
   */
  BigInteger mc_project_attachment_add(String username,
                                       String password,
                                       BigInteger project_id,
                                       String name,
                                       String title,
                                       String description,
                                       String file_type,
                                       byte[] content) throws RemoteException;

  /**
   * Delete the project attachment with the specified id.
   */
  boolean mc_project_attachment_delete(String username, String password, BigInteger project_attachment_id) throws RemoteException;

  /**
   * Get the subprojects ID of a specific project.
   */
  String[] mc_project_get_all_subprojects(String username, String password, BigInteger project_id) throws RemoteException;

  /**
   * Get the filters defined for the specified project.
   */
  FilterData[] mc_filter_get(String username, String password, BigInteger project_id) throws RemoteException;

  /**
   * Get the issues that match the specified filter and paging details.
   */
  IssueData[] mc_filter_get_issues(String username,
                                   String password,
                                   BigInteger project_id,
                                   BigInteger filter_id,
                                   BigInteger page_number,
                                   BigInteger per_page) throws RemoteException;

  /**
   * Get the issue headers that match the specified filter and paging
   * details.
   */
  IssueHeaderData[] mc_filter_get_issue_headers(String username,
                                                String password,
                                                BigInteger project_id,
                                                BigInteger filter_id,
                                                BigInteger page_number,
                                                BigInteger per_page) throws RemoteException;

  /**
   * Get the value for the specified configuration variable.
   */
  String mc_config_get_string(String username, String password, String config_var) throws RemoteException;

  /**
   * Notifies MantisBT of a check-in for the issue with the specified
   * id.
   */
  boolean mc_issue_checkin(String username, String password, BigInteger issue_id, String comment, boolean fixed) throws RemoteException;

  /**
   * Get the value for the specified user preference.
   */
  String mc_user_pref_get_pref(String username, String password, BigInteger project_id, String pref_name) throws RemoteException;

  /**
   * Get profiles available to the current user.
   */
  ProfileDataSearchResult mc_user_profiles_get_all(String username,
                                                   String password,
                                                   BigInteger page_number,
                                                   BigInteger per_page) throws RemoteException;

  /**
   * Gets all the tags.
   */
  TagDataSearchResult mc_tag_get_all(String username,
                                     String password,
                                     BigInteger page_number,
                                     BigInteger per_page) throws RemoteException;

  /**
   * Creates a tag.
   */
  BigInteger mc_tag_add(String username, String password, TagData tag) throws RemoteException;

  /**
   * Deletes a tag.
   */
  boolean mc_tag_delete(String username, String password, BigInteger tag_id) throws RemoteException;
}
