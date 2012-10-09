/**
 * MantisConnectPortType.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis 1.4 Apr 22, 2006 (06:55:48 PDT) WSDL2Java emitter.
 */

package com.intellij.tasks.mantis.model;

public interface MantisConnectPortType extends java.rmi.Remote {
    public java.lang.String mc_version() throws java.rmi.RemoteException;

    /**
     * Get the enumeration for statuses.
     */
    public com.intellij.tasks.mantis.model.ObjectRef[] mc_enum_status(java.lang.String username, java.lang.String password) throws java.rmi.RemoteException;

    /**
     * Get the enumeration for priorities.
     */
    public com.intellij.tasks.mantis.model.ObjectRef[] mc_enum_priorities(java.lang.String username, java.lang.String password) throws java.rmi.RemoteException;

    /**
     * Get the enumeration for severities.
     */
    public com.intellij.tasks.mantis.model.ObjectRef[] mc_enum_severities(java.lang.String username, java.lang.String password) throws java.rmi.RemoteException;

    /**
     * Get the enumeration for reproducibilities.
     */
    public com.intellij.tasks.mantis.model.ObjectRef[] mc_enum_reproducibilities(java.lang.String username, java.lang.String password) throws java.rmi.RemoteException;

    /**
     * Get the enumeration for projections.
     */
    public com.intellij.tasks.mantis.model.ObjectRef[] mc_enum_projections(java.lang.String username, java.lang.String password) throws java.rmi.RemoteException;

    /**
     * Get the enumeration for ETAs.
     */
    public com.intellij.tasks.mantis.model.ObjectRef[] mc_enum_etas(java.lang.String username, java.lang.String password) throws java.rmi.RemoteException;

    /**
     * Get the enumeration for resolutions.
     */
    public com.intellij.tasks.mantis.model.ObjectRef[] mc_enum_resolutions(java.lang.String username, java.lang.String password) throws java.rmi.RemoteException;

    /**
     * Get the enumeration for access levels.
     */
    public com.intellij.tasks.mantis.model.ObjectRef[] mc_enum_access_levels(java.lang.String username, java.lang.String password) throws java.rmi.RemoteException;

    /**
     * Get the enumeration for project statuses.
     */
    public com.intellij.tasks.mantis.model.ObjectRef[] mc_enum_project_status(java.lang.String username, java.lang.String password) throws java.rmi.RemoteException;

    /**
     * Get the enumeration for project view states.
     */
    public com.intellij.tasks.mantis.model.ObjectRef[] mc_enum_project_view_states(java.lang.String username, java.lang.String password) throws java.rmi.RemoteException;

    /**
     * Get the enumeration for view states.
     */
    public com.intellij.tasks.mantis.model.ObjectRef[] mc_enum_view_states(java.lang.String username, java.lang.String password) throws java.rmi.RemoteException;

    /**
     * Get the enumeration for custom field types.
     */
    public com.intellij.tasks.mantis.model.ObjectRef[] mc_enum_custom_field_types(java.lang.String username, java.lang.String password) throws java.rmi.RemoteException;

    /**
     * Get the enumeration for the specified enumeration type.
     */
    public java.lang.String mc_enum_get(java.lang.String username, java.lang.String password, java.lang.String enumeration) throws java.rmi.RemoteException;

    /**
     * Check there exists an issue with the specified id.
     */
    public boolean mc_issue_exists(java.lang.String username, java.lang.String password, java.math.BigInteger issue_id) throws java.rmi.RemoteException;

    /**
     * Get the issue with the specified id.
     */
    public com.intellij.tasks.mantis.model.IssueData mc_issue_get(java.lang.String username, java.lang.String password, java.math.BigInteger issue_id) throws java.rmi.RemoteException;

    /**
     * Get the latest submitted issue in the specified project.
     */
    public java.math.BigInteger mc_issue_get_biggest_id(java.lang.String username, java.lang.String password, java.math.BigInteger project_id) throws java.rmi.RemoteException;

    /**
     * Get the id of the issue with the specified summary.
     */
    public java.math.BigInteger mc_issue_get_id_from_summary(java.lang.String username, java.lang.String password, java.lang.String summary) throws java.rmi.RemoteException;

    /**
     * Submit the specified issue details.
     */
    public java.math.BigInteger mc_issue_add(java.lang.String username, java.lang.String password, com.intellij.tasks.mantis.model.IssueData issue) throws java.rmi.RemoteException;

    /**
     * Update Issue method.
     */
    public boolean mc_issue_update(java.lang.String username, java.lang.String password, java.math.BigInteger issueId, com.intellij.tasks.mantis.model.IssueData issue) throws java.rmi.RemoteException;

    /**
     * Sets the tags for a specified issue.
     */
    public boolean mc_issue_set_tags(java.lang.String username, java.lang.String password, java.math.BigInteger issue_id, com.intellij.tasks.mantis.model.TagData[] tags) throws java.rmi.RemoteException;

    /**
     * Delete the issue with the specified id.
     */
    public boolean mc_issue_delete(java.lang.String username, java.lang.String password, java.math.BigInteger issue_id) throws java.rmi.RemoteException;

    /**
     * Submit a new note.
     */
    public java.math.BigInteger mc_issue_note_add(java.lang.String username, java.lang.String password, java.math.BigInteger issue_id, com.intellij.tasks.mantis.model.IssueNoteData note) throws java.rmi.RemoteException;

    /**
     * Delete the note with the specified id.
     */
    public boolean mc_issue_note_delete(java.lang.String username, java.lang.String password, java.math.BigInteger issue_note_id) throws java.rmi.RemoteException;

    /**
     * Update a specific note of a specific issue.
     */
    public boolean mc_issue_note_update(java.lang.String username, java.lang.String password, com.intellij.tasks.mantis.model.IssueNoteData note) throws java.rmi.RemoteException;

    /**
     * Submit a new relationship.
     */
    public java.math.BigInteger mc_issue_relationship_add(java.lang.String username, java.lang.String password, java.math.BigInteger issue_id, com.intellij.tasks.mantis.model.RelationshipData relationship) throws java.rmi.RemoteException;

    /**
     * Delete the relationship for the specified issue.
     */
    public boolean mc_issue_relationship_delete(java.lang.String username, java.lang.String password, java.math.BigInteger issue_id, java.math.BigInteger relationship_id) throws java.rmi.RemoteException;

    /**
     * Submit a new issue attachment.
     */
    public java.math.BigInteger mc_issue_attachment_add(java.lang.String username, java.lang.String password, java.math.BigInteger issue_id, java.lang.String name, java.lang.String file_type, byte[] content) throws java.rmi.RemoteException;

    /**
     * Delete the issue attachment with the specified id.
     */
    public boolean mc_issue_attachment_delete(java.lang.String username, java.lang.String password, java.math.BigInteger issue_attachment_id) throws java.rmi.RemoteException;

    /**
     * Get the data for the specified issue attachment.
     */
    public byte[] mc_issue_attachment_get(java.lang.String username, java.lang.String password, java.math.BigInteger issue_attachment_id) throws java.rmi.RemoteException;

    /**
     * Add a new project to the tracker (must have admin privileges)
     */
    public java.math.BigInteger mc_project_add(java.lang.String username, java.lang.String password, com.intellij.tasks.mantis.model.ProjectData project) throws java.rmi.RemoteException;

    /**
     * Add a new project to the tracker (must have admin privileges)
     */
    public boolean mc_project_delete(java.lang.String username, java.lang.String password, java.math.BigInteger project_id) throws java.rmi.RemoteException;

    /**
     * Update a specific project to the tracker (must have admin privileges)
     */
    public boolean mc_project_update(java.lang.String username, java.lang.String password, java.math.BigInteger project_id, com.intellij.tasks.mantis.model.ProjectData project) throws java.rmi.RemoteException;

    /**
     * Get the id of the project with the specified name.
     */
    public java.math.BigInteger mc_project_get_id_from_name(java.lang.String username, java.lang.String password, java.lang.String project_name) throws java.rmi.RemoteException;

    /**
     * Get the issues that match the specified project id and paging
     * details.
     */
    public com.intellij.tasks.mantis.model.IssueData[] mc_project_get_issues(java.lang.String username, java.lang.String password, java.math.BigInteger project_id, java.math.BigInteger page_number, java.math.BigInteger per_page) throws java.rmi.RemoteException;

    /**
     * Get the issue headers that match the specified project id and
     * paging details.
     */
    public com.intellij.tasks.mantis.model.IssueHeaderData[] mc_project_get_issue_headers(java.lang.String username, java.lang.String password, java.math.BigInteger project_id, java.math.BigInteger page_number, java.math.BigInteger per_page) throws java.rmi.RemoteException;

    /**
     * Get appropriate users assigned to a project by access level.
     */
    public com.intellij.tasks.mantis.model.AccountData[] mc_project_get_users(java.lang.String username, java.lang.String password, java.math.BigInteger project_id, java.math.BigInteger access) throws java.rmi.RemoteException;

    /**
     * Get the list of projects that are accessible to the logged
     * in user.
     */
    public com.intellij.tasks.mantis.model.ProjectData[] mc_projects_get_user_accessible(java.lang.String username, java.lang.String password) throws java.rmi.RemoteException;

    /**
     * Get the categories belonging to the specified project.
     */
    public java.lang.String[] mc_project_get_categories(java.lang.String username, java.lang.String password, java.math.BigInteger project_id) throws java.rmi.RemoteException;

    /**
     * Add a category of specific project.
     */
    public java.math.BigInteger mc_project_add_category(java.lang.String username, java.lang.String password, java.math.BigInteger project_id, java.lang.String p_category_name) throws java.rmi.RemoteException;

    /**
     * Delete a category of specific project.
     */
    public java.math.BigInteger mc_project_delete_category(java.lang.String username, java.lang.String password, java.math.BigInteger project_id, java.lang.String p_category_name) throws java.rmi.RemoteException;

    /**
     * Rename a category of specific project.
     */
    public java.math.BigInteger mc_project_rename_category_by_name(java.lang.String username, java.lang.String password, java.math.BigInteger project_id, java.lang.String p_category_name, java.lang.String p_category_name_new, java.math.BigInteger p_assigned_to) throws java.rmi.RemoteException;

    /**
     * Get the versions belonging to the specified project.
     */
    public com.intellij.tasks.mantis.model.ProjectVersionData[] mc_project_get_versions(java.lang.String username, java.lang.String password, java.math.BigInteger project_id) throws java.rmi.RemoteException;

    /**
     * Submit the specified version details.
     */
    public java.math.BigInteger mc_project_version_add(java.lang.String username, java.lang.String password, com.intellij.tasks.mantis.model.ProjectVersionData version) throws java.rmi.RemoteException;

    /**
     * Update version method.
     */
    public boolean mc_project_version_update(java.lang.String username, java.lang.String password, java.math.BigInteger version_id, com.intellij.tasks.mantis.model.ProjectVersionData version) throws java.rmi.RemoteException;

    /**
     * Delete the version with the specified id.
     */
    public boolean mc_project_version_delete(java.lang.String username, java.lang.String password, java.math.BigInteger version_id) throws java.rmi.RemoteException;

    /**
     * Get the released versions that belong to the specified project.
     */
    public com.intellij.tasks.mantis.model.ProjectVersionData[] mc_project_get_released_versions(java.lang.String username, java.lang.String password, java.math.BigInteger project_id) throws java.rmi.RemoteException;

    /**
     * Get the unreleased version that belong to the specified project.
     */
    public com.intellij.tasks.mantis.model.ProjectVersionData[] mc_project_get_unreleased_versions(java.lang.String username, java.lang.String password, java.math.BigInteger project_id) throws java.rmi.RemoteException;

    /**
     * Get the attachments that belong to the specified project.
     */
    public com.intellij.tasks.mantis.model.ProjectAttachmentData[] mc_project_get_attachments(java.lang.String username, java.lang.String password, java.math.BigInteger project_id) throws java.rmi.RemoteException;

    /**
     * Get the custom fields that belong to the specified project.
     */
    public com.intellij.tasks.mantis.model.CustomFieldDefinitionData[] mc_project_get_custom_fields(java.lang.String username, java.lang.String password, java.math.BigInteger project_id) throws java.rmi.RemoteException;

    /**
     * Get the data for the specified project attachment.
     */
    public byte[] mc_project_attachment_get(java.lang.String username, java.lang.String password, java.math.BigInteger project_attachment_id) throws java.rmi.RemoteException;

    /**
     * Submit a new project attachment.
     */
    public java.math.BigInteger mc_project_attachment_add(java.lang.String username, java.lang.String password, java.math.BigInteger project_id, java.lang.String name, java.lang.String title, java.lang.String description, java.lang.String file_type, byte[] content) throws java.rmi.RemoteException;

    /**
     * Delete the project attachment with the specified id.
     */
    public boolean mc_project_attachment_delete(java.lang.String username, java.lang.String password, java.math.BigInteger project_attachment_id) throws java.rmi.RemoteException;

    /**
     * Get the subprojects ID of a specific project.
     */
    public java.lang.String[] mc_project_get_all_subprojects(java.lang.String username, java.lang.String password, java.math.BigInteger project_id) throws java.rmi.RemoteException;

    /**
     * Get the filters defined for the specified project.
     */
    public com.intellij.tasks.mantis.model.FilterData[] mc_filter_get(java.lang.String username, java.lang.String password, java.math.BigInteger project_id) throws java.rmi.RemoteException;

    /**
     * Get the issues that match the specified filter and paging details.
     */
    public com.intellij.tasks.mantis.model.IssueData[] mc_filter_get_issues(java.lang.String username, java.lang.String password, java.math.BigInteger project_id, java.math.BigInteger filter_id, java.math.BigInteger page_number, java.math.BigInteger per_page) throws java.rmi.RemoteException;

    /**
     * Get the issue headers that match the specified filter and paging
     * details.
     */
    public com.intellij.tasks.mantis.model.IssueHeaderData[] mc_filter_get_issue_headers(java.lang.String username, java.lang.String password, java.math.BigInteger project_id, java.math.BigInteger filter_id, java.math.BigInteger page_number, java.math.BigInteger per_page) throws java.rmi.RemoteException;

    /**
     * Get the value for the specified configuration variable.
     */
    public java.lang.String mc_config_get_string(java.lang.String username, java.lang.String password, java.lang.String config_var) throws java.rmi.RemoteException;

    /**
     * Notifies MantisBT of a check-in for the issue with the specified
     * id.
     */
    public boolean mc_issue_checkin(java.lang.String username, java.lang.String password, java.math.BigInteger issue_id, java.lang.String comment, boolean fixed) throws java.rmi.RemoteException;

    /**
     * Get the value for the specified user preference.
     */
    public java.lang.String mc_user_pref_get_pref(java.lang.String username, java.lang.String password, java.math.BigInteger project_id, java.lang.String pref_name) throws java.rmi.RemoteException;

    /**
     * Get profiles available to the current user.
     */
    public com.intellij.tasks.mantis.model.ProfileDataSearchResult mc_user_profiles_get_all(java.lang.String username, java.lang.String password, java.math.BigInteger page_number, java.math.BigInteger per_page) throws java.rmi.RemoteException;

    /**
     * Gets all the tags.
     */
    public com.intellij.tasks.mantis.model.TagDataSearchResult mc_tag_get_all(java.lang.String username, java.lang.String password, java.math.BigInteger page_number, java.math.BigInteger per_page) throws java.rmi.RemoteException;

    /**
     * Creates a tag.
     */
    public java.math.BigInteger mc_tag_add(java.lang.String username, java.lang.String password, com.intellij.tasks.mantis.model.TagData tag) throws java.rmi.RemoteException;

    /**
     * Deletes a tag.
     */
    public boolean mc_tag_delete(java.lang.String username, java.lang.String password, java.math.BigInteger tag_id) throws java.rmi.RemoteException;
}
