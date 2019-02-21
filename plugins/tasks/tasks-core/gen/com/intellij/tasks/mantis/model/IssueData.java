/**
 * IssueData.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis 1.4 Apr 22, 2006 (06:55:48 PDT) WSDL2Java emitter.
 */

package com.intellij.tasks.mantis.model;

public class IssueData  implements java.io.Serializable {
    private java.math.BigInteger id;

    private com.intellij.tasks.mantis.model.ObjectRef view_state;

    private java.util.Calendar last_updated;

    private com.intellij.tasks.mantis.model.ObjectRef project;

    private java.lang.String category;

    private com.intellij.tasks.mantis.model.ObjectRef priority;

    private com.intellij.tasks.mantis.model.ObjectRef severity;

    private com.intellij.tasks.mantis.model.ObjectRef status;

    private com.intellij.tasks.mantis.model.AccountData reporter;

    private java.lang.String summary;

    private java.lang.String version;

    private java.lang.String build;

    private java.lang.String platform;

    private java.lang.String os;

    private java.lang.String os_build;

    private com.intellij.tasks.mantis.model.ObjectRef reproducibility;

    private java.util.Calendar date_submitted;

    private java.math.BigInteger sponsorship_total;

    private com.intellij.tasks.mantis.model.AccountData handler;

    private com.intellij.tasks.mantis.model.ObjectRef projection;

    private com.intellij.tasks.mantis.model.ObjectRef eta;

    private com.intellij.tasks.mantis.model.ObjectRef resolution;

    private java.lang.String fixed_in_version;

    private java.lang.String target_version;

    private java.lang.String description;

    private java.lang.String steps_to_reproduce;

    private java.lang.String additional_information;

    private com.intellij.tasks.mantis.model.AttachmentData[] attachments;

    private com.intellij.tasks.mantis.model.RelationshipData[] relationships;

    private com.intellij.tasks.mantis.model.IssueNoteData[] notes;

    private com.intellij.tasks.mantis.model.CustomFieldValueForIssueData[] custom_fields;

    private java.util.Calendar due_date;

    private com.intellij.tasks.mantis.model.AccountData[] monitors;

    private java.lang.Boolean sticky;

    private com.intellij.tasks.mantis.model.ObjectRef[] tags;

    public IssueData() {
    }

    public IssueData(
           java.math.BigInteger id,
           com.intellij.tasks.mantis.model.ObjectRef view_state,
           java.util.Calendar last_updated,
           com.intellij.tasks.mantis.model.ObjectRef project,
           java.lang.String category,
           com.intellij.tasks.mantis.model.ObjectRef priority,
           com.intellij.tasks.mantis.model.ObjectRef severity,
           com.intellij.tasks.mantis.model.ObjectRef status,
           com.intellij.tasks.mantis.model.AccountData reporter,
           java.lang.String summary,
           java.lang.String version,
           java.lang.String build,
           java.lang.String platform,
           java.lang.String os,
           java.lang.String os_build,
           com.intellij.tasks.mantis.model.ObjectRef reproducibility,
           java.util.Calendar date_submitted,
           java.math.BigInteger sponsorship_total,
           com.intellij.tasks.mantis.model.AccountData handler,
           com.intellij.tasks.mantis.model.ObjectRef projection,
           com.intellij.tasks.mantis.model.ObjectRef eta,
           com.intellij.tasks.mantis.model.ObjectRef resolution,
           java.lang.String fixed_in_version,
           java.lang.String target_version,
           java.lang.String description,
           java.lang.String steps_to_reproduce,
           java.lang.String additional_information,
           com.intellij.tasks.mantis.model.AttachmentData[] attachments,
           com.intellij.tasks.mantis.model.RelationshipData[] relationships,
           com.intellij.tasks.mantis.model.IssueNoteData[] notes,
           com.intellij.tasks.mantis.model.CustomFieldValueForIssueData[] custom_fields,
           java.util.Calendar due_date,
           com.intellij.tasks.mantis.model.AccountData[] monitors,
           java.lang.Boolean sticky,
           com.intellij.tasks.mantis.model.ObjectRef[] tags) {
           this.id = id;
           this.view_state = view_state;
           this.last_updated = last_updated;
           this.project = project;
           this.category = category;
           this.priority = priority;
           this.severity = severity;
           this.status = status;
           this.reporter = reporter;
           this.summary = summary;
           this.version = version;
           this.build = build;
           this.platform = platform;
           this.os = os;
           this.os_build = os_build;
           this.reproducibility = reproducibility;
           this.date_submitted = date_submitted;
           this.sponsorship_total = sponsorship_total;
           this.handler = handler;
           this.projection = projection;
           this.eta = eta;
           this.resolution = resolution;
           this.fixed_in_version = fixed_in_version;
           this.target_version = target_version;
           this.description = description;
           this.steps_to_reproduce = steps_to_reproduce;
           this.additional_information = additional_information;
           this.attachments = attachments;
           this.relationships = relationships;
           this.notes = notes;
           this.custom_fields = custom_fields;
           this.due_date = due_date;
           this.monitors = monitors;
           this.sticky = sticky;
           this.tags = tags;
    }


    /**
     * Gets the id value for this IssueData.
     * 
     * @return id
     */
    public java.math.BigInteger getId() {
        return id;
    }


    /**
     * Sets the id value for this IssueData.
     * 
     * @param id
     */
    public void setId(java.math.BigInteger id) {
        this.id = id;
    }


    /**
     * Gets the view_state value for this IssueData.
     * 
     * @return view_state
     */
    public com.intellij.tasks.mantis.model.ObjectRef getView_state() {
        return view_state;
    }


    /**
     * Sets the view_state value for this IssueData.
     * 
     * @param view_state
     */
    public void setView_state(com.intellij.tasks.mantis.model.ObjectRef view_state) {
        this.view_state = view_state;
    }


    /**
     * Gets the last_updated value for this IssueData.
     * 
     * @return last_updated
     */
    public java.util.Calendar getLast_updated() {
        return last_updated;
    }


    /**
     * Sets the last_updated value for this IssueData.
     * 
     * @param last_updated
     */
    public void setLast_updated(java.util.Calendar last_updated) {
        this.last_updated = last_updated;
    }


    /**
     * Gets the project value for this IssueData.
     * 
     * @return project
     */
    public com.intellij.tasks.mantis.model.ObjectRef getProject() {
        return project;
    }


    /**
     * Sets the project value for this IssueData.
     * 
     * @param project
     */
    public void setProject(com.intellij.tasks.mantis.model.ObjectRef project) {
        this.project = project;
    }


    /**
     * Gets the category value for this IssueData.
     * 
     * @return category
     */
    public java.lang.String getCategory() {
        return category;
    }


    /**
     * Sets the category value for this IssueData.
     * 
     * @param category
     */
    public void setCategory(java.lang.String category) {
        this.category = category;
    }


    /**
     * Gets the priority value for this IssueData.
     * 
     * @return priority
     */
    public com.intellij.tasks.mantis.model.ObjectRef getPriority() {
        return priority;
    }


    /**
     * Sets the priority value for this IssueData.
     * 
     * @param priority
     */
    public void setPriority(com.intellij.tasks.mantis.model.ObjectRef priority) {
        this.priority = priority;
    }


    /**
     * Gets the severity value for this IssueData.
     * 
     * @return severity
     */
    public com.intellij.tasks.mantis.model.ObjectRef getSeverity() {
        return severity;
    }


    /**
     * Sets the severity value for this IssueData.
     * 
     * @param severity
     */
    public void setSeverity(com.intellij.tasks.mantis.model.ObjectRef severity) {
        this.severity = severity;
    }


    /**
     * Gets the status value for this IssueData.
     * 
     * @return status
     */
    public com.intellij.tasks.mantis.model.ObjectRef getStatus() {
        return status;
    }


    /**
     * Sets the status value for this IssueData.
     * 
     * @param status
     */
    public void setStatus(com.intellij.tasks.mantis.model.ObjectRef status) {
        this.status = status;
    }


    /**
     * Gets the reporter value for this IssueData.
     * 
     * @return reporter
     */
    public com.intellij.tasks.mantis.model.AccountData getReporter() {
        return reporter;
    }


    /**
     * Sets the reporter value for this IssueData.
     * 
     * @param reporter
     */
    public void setReporter(com.intellij.tasks.mantis.model.AccountData reporter) {
        this.reporter = reporter;
    }


    /**
     * Gets the summary value for this IssueData.
     * 
     * @return summary
     */
    public java.lang.String getSummary() {
        return summary;
    }


    /**
     * Sets the summary value for this IssueData.
     * 
     * @param summary
     */
    public void setSummary(java.lang.String summary) {
        this.summary = summary;
    }


    /**
     * Gets the version value for this IssueData.
     * 
     * @return version
     */
    public java.lang.String getVersion() {
        return version;
    }


    /**
     * Sets the version value for this IssueData.
     * 
     * @param version
     */
    public void setVersion(java.lang.String version) {
        this.version = version;
    }


    /**
     * Gets the build value for this IssueData.
     * 
     * @return build
     */
    public java.lang.String getBuild() {
        return build;
    }


    /**
     * Sets the build value for this IssueData.
     * 
     * @param build
     */
    public void setBuild(java.lang.String build) {
        this.build = build;
    }


    /**
     * Gets the platform value for this IssueData.
     * 
     * @return platform
     */
    public java.lang.String getPlatform() {
        return platform;
    }


    /**
     * Sets the platform value for this IssueData.
     * 
     * @param platform
     */
    public void setPlatform(java.lang.String platform) {
        this.platform = platform;
    }


    /**
     * Gets the os value for this IssueData.
     * 
     * @return os
     */
    public java.lang.String getOs() {
        return os;
    }


    /**
     * Sets the os value for this IssueData.
     * 
     * @param os
     */
    public void setOs(java.lang.String os) {
        this.os = os;
    }


    /**
     * Gets the os_build value for this IssueData.
     * 
     * @return os_build
     */
    public java.lang.String getOs_build() {
        return os_build;
    }


    /**
     * Sets the os_build value for this IssueData.
     * 
     * @param os_build
     */
    public void setOs_build(java.lang.String os_build) {
        this.os_build = os_build;
    }


    /**
     * Gets the reproducibility value for this IssueData.
     * 
     * @return reproducibility
     */
    public com.intellij.tasks.mantis.model.ObjectRef getReproducibility() {
        return reproducibility;
    }


    /**
     * Sets the reproducibility value for this IssueData.
     * 
     * @param reproducibility
     */
    public void setReproducibility(com.intellij.tasks.mantis.model.ObjectRef reproducibility) {
        this.reproducibility = reproducibility;
    }


    /**
     * Gets the date_submitted value for this IssueData.
     * 
     * @return date_submitted
     */
    public java.util.Calendar getDate_submitted() {
        return date_submitted;
    }


    /**
     * Sets the date_submitted value for this IssueData.
     * 
     * @param date_submitted
     */
    public void setDate_submitted(java.util.Calendar date_submitted) {
        this.date_submitted = date_submitted;
    }


    /**
     * Gets the sponsorship_total value for this IssueData.
     * 
     * @return sponsorship_total
     */
    public java.math.BigInteger getSponsorship_total() {
        return sponsorship_total;
    }


    /**
     * Sets the sponsorship_total value for this IssueData.
     * 
     * @param sponsorship_total
     */
    public void setSponsorship_total(java.math.BigInteger sponsorship_total) {
        this.sponsorship_total = sponsorship_total;
    }


    /**
     * Gets the handler value for this IssueData.
     * 
     * @return handler
     */
    public com.intellij.tasks.mantis.model.AccountData getHandler() {
        return handler;
    }


    /**
     * Sets the handler value for this IssueData.
     * 
     * @param handler
     */
    public void setHandler(com.intellij.tasks.mantis.model.AccountData handler) {
        this.handler = handler;
    }


    /**
     * Gets the projection value for this IssueData.
     * 
     * @return projection
     */
    public com.intellij.tasks.mantis.model.ObjectRef getProjection() {
        return projection;
    }


    /**
     * Sets the projection value for this IssueData.
     * 
     * @param projection
     */
    public void setProjection(com.intellij.tasks.mantis.model.ObjectRef projection) {
        this.projection = projection;
    }


    /**
     * Gets the eta value for this IssueData.
     * 
     * @return eta
     */
    public com.intellij.tasks.mantis.model.ObjectRef getEta() {
        return eta;
    }


    /**
     * Sets the eta value for this IssueData.
     * 
     * @param eta
     */
    public void setEta(com.intellij.tasks.mantis.model.ObjectRef eta) {
        this.eta = eta;
    }


    /**
     * Gets the resolution value for this IssueData.
     * 
     * @return resolution
     */
    public com.intellij.tasks.mantis.model.ObjectRef getResolution() {
        return resolution;
    }


    /**
     * Sets the resolution value for this IssueData.
     * 
     * @param resolution
     */
    public void setResolution(com.intellij.tasks.mantis.model.ObjectRef resolution) {
        this.resolution = resolution;
    }


    /**
     * Gets the fixed_in_version value for this IssueData.
     * 
     * @return fixed_in_version
     */
    public java.lang.String getFixed_in_version() {
        return fixed_in_version;
    }


    /**
     * Sets the fixed_in_version value for this IssueData.
     * 
     * @param fixed_in_version
     */
    public void setFixed_in_version(java.lang.String fixed_in_version) {
        this.fixed_in_version = fixed_in_version;
    }


    /**
     * Gets the target_version value for this IssueData.
     * 
     * @return target_version
     */
    public java.lang.String getTarget_version() {
        return target_version;
    }


    /**
     * Sets the target_version value for this IssueData.
     * 
     * @param target_version
     */
    public void setTarget_version(java.lang.String target_version) {
        this.target_version = target_version;
    }


    /**
     * Gets the description value for this IssueData.
     * 
     * @return description
     */
    public java.lang.String getDescription() {
        return description;
    }


    /**
     * Sets the description value for this IssueData.
     * 
     * @param description
     */
    public void setDescription(java.lang.String description) {
        this.description = description;
    }


    /**
     * Gets the steps_to_reproduce value for this IssueData.
     * 
     * @return steps_to_reproduce
     */
    public java.lang.String getSteps_to_reproduce() {
        return steps_to_reproduce;
    }


    /**
     * Sets the steps_to_reproduce value for this IssueData.
     * 
     * @param steps_to_reproduce
     */
    public void setSteps_to_reproduce(java.lang.String steps_to_reproduce) {
        this.steps_to_reproduce = steps_to_reproduce;
    }


    /**
     * Gets the additional_information value for this IssueData.
     * 
     * @return additional_information
     */
    public java.lang.String getAdditional_information() {
        return additional_information;
    }


    /**
     * Sets the additional_information value for this IssueData.
     * 
     * @param additional_information
     */
    public void setAdditional_information(java.lang.String additional_information) {
        this.additional_information = additional_information;
    }


    /**
     * Gets the attachments value for this IssueData.
     * 
     * @return attachments
     */
    public com.intellij.tasks.mantis.model.AttachmentData[] getAttachments() {
        return attachments;
    }


    /**
     * Sets the attachments value for this IssueData.
     * 
     * @param attachments
     */
    public void setAttachments(com.intellij.tasks.mantis.model.AttachmentData[] attachments) {
        this.attachments = attachments;
    }


    /**
     * Gets the relationships value for this IssueData.
     * 
     * @return relationships
     */
    public com.intellij.tasks.mantis.model.RelationshipData[] getRelationships() {
        return relationships;
    }


    /**
     * Sets the relationships value for this IssueData.
     * 
     * @param relationships
     */
    public void setRelationships(com.intellij.tasks.mantis.model.RelationshipData[] relationships) {
        this.relationships = relationships;
    }


    /**
     * Gets the notes value for this IssueData.
     * 
     * @return notes
     */
    public com.intellij.tasks.mantis.model.IssueNoteData[] getNotes() {
        return notes;
    }


    /**
     * Sets the notes value for this IssueData.
     * 
     * @param notes
     */
    public void setNotes(com.intellij.tasks.mantis.model.IssueNoteData[] notes) {
        this.notes = notes;
    }


    /**
     * Gets the custom_fields value for this IssueData.
     * 
     * @return custom_fields
     */
    public com.intellij.tasks.mantis.model.CustomFieldValueForIssueData[] getCustom_fields() {
        return custom_fields;
    }


    /**
     * Sets the custom_fields value for this IssueData.
     * 
     * @param custom_fields
     */
    public void setCustom_fields(com.intellij.tasks.mantis.model.CustomFieldValueForIssueData[] custom_fields) {
        this.custom_fields = custom_fields;
    }


    /**
     * Gets the due_date value for this IssueData.
     * 
     * @return due_date
     */
    public java.util.Calendar getDue_date() {
        return due_date;
    }


    /**
     * Sets the due_date value for this IssueData.
     * 
     * @param due_date
     */
    public void setDue_date(java.util.Calendar due_date) {
        this.due_date = due_date;
    }


    /**
     * Gets the monitors value for this IssueData.
     * 
     * @return monitors
     */
    public com.intellij.tasks.mantis.model.AccountData[] getMonitors() {
        return monitors;
    }


    /**
     * Sets the monitors value for this IssueData.
     * 
     * @param monitors
     */
    public void setMonitors(com.intellij.tasks.mantis.model.AccountData[] monitors) {
        this.monitors = monitors;
    }


    /**
     * Gets the sticky value for this IssueData.
     * 
     * @return sticky
     */
    public java.lang.Boolean getSticky() {
        return sticky;
    }


    /**
     * Sets the sticky value for this IssueData.
     * 
     * @param sticky
     */
    public void setSticky(java.lang.Boolean sticky) {
        this.sticky = sticky;
    }


    /**
     * Gets the tags value for this IssueData.
     * 
     * @return tags
     */
    public com.intellij.tasks.mantis.model.ObjectRef[] getTags() {
        return tags;
    }


    /**
     * Sets the tags value for this IssueData.
     * 
     * @param tags
     */
    public void setTags(com.intellij.tasks.mantis.model.ObjectRef[] tags) {
        this.tags = tags;
    }

    private java.lang.Object __equalsCalc = null;
    public synchronized boolean equals(java.lang.Object obj) {
        if (!(obj instanceof IssueData)) return false;
        IssueData other = (IssueData) obj;
        if (obj == null) return false;
        if (this == obj) return true;
        if (__equalsCalc != null) {
            return (__equalsCalc == obj);
        }
        __equalsCalc = obj;
        boolean _equals;
        _equals = true && 
            ((this.id==null && other.getId()==null) || 
             (this.id!=null &&
              this.id.equals(other.getId()))) &&
            ((this.view_state==null && other.getView_state()==null) || 
             (this.view_state!=null &&
              this.view_state.equals(other.getView_state()))) &&
            ((this.last_updated==null && other.getLast_updated()==null) || 
             (this.last_updated!=null &&
              this.last_updated.equals(other.getLast_updated()))) &&
            ((this.project==null && other.getProject()==null) || 
             (this.project!=null &&
              this.project.equals(other.getProject()))) &&
            ((this.category==null && other.getCategory()==null) || 
             (this.category!=null &&
              this.category.equals(other.getCategory()))) &&
            ((this.priority==null && other.getPriority()==null) || 
             (this.priority!=null &&
              this.priority.equals(other.getPriority()))) &&
            ((this.severity==null && other.getSeverity()==null) || 
             (this.severity!=null &&
              this.severity.equals(other.getSeverity()))) &&
            ((this.status==null && other.getStatus()==null) || 
             (this.status!=null &&
              this.status.equals(other.getStatus()))) &&
            ((this.reporter==null && other.getReporter()==null) || 
             (this.reporter!=null &&
              this.reporter.equals(other.getReporter()))) &&
            ((this.summary==null && other.getSummary()==null) || 
             (this.summary!=null &&
              this.summary.equals(other.getSummary()))) &&
            ((this.version==null && other.getVersion()==null) || 
             (this.version!=null &&
              this.version.equals(other.getVersion()))) &&
            ((this.build==null && other.getBuild()==null) || 
             (this.build!=null &&
              this.build.equals(other.getBuild()))) &&
            ((this.platform==null && other.getPlatform()==null) || 
             (this.platform!=null &&
              this.platform.equals(other.getPlatform()))) &&
            ((this.os==null && other.getOs()==null) || 
             (this.os!=null &&
              this.os.equals(other.getOs()))) &&
            ((this.os_build==null && other.getOs_build()==null) || 
             (this.os_build!=null &&
              this.os_build.equals(other.getOs_build()))) &&
            ((this.reproducibility==null && other.getReproducibility()==null) || 
             (this.reproducibility!=null &&
              this.reproducibility.equals(other.getReproducibility()))) &&
            ((this.date_submitted==null && other.getDate_submitted()==null) || 
             (this.date_submitted!=null &&
              this.date_submitted.equals(other.getDate_submitted()))) &&
            ((this.sponsorship_total==null && other.getSponsorship_total()==null) || 
             (this.sponsorship_total!=null &&
              this.sponsorship_total.equals(other.getSponsorship_total()))) &&
            ((this.handler==null && other.getHandler()==null) || 
             (this.handler!=null &&
              this.handler.equals(other.getHandler()))) &&
            ((this.projection==null && other.getProjection()==null) || 
             (this.projection!=null &&
              this.projection.equals(other.getProjection()))) &&
            ((this.eta==null && other.getEta()==null) || 
             (this.eta!=null &&
              this.eta.equals(other.getEta()))) &&
            ((this.resolution==null && other.getResolution()==null) || 
             (this.resolution!=null &&
              this.resolution.equals(other.getResolution()))) &&
            ((this.fixed_in_version==null && other.getFixed_in_version()==null) || 
             (this.fixed_in_version!=null &&
              this.fixed_in_version.equals(other.getFixed_in_version()))) &&
            ((this.target_version==null && other.getTarget_version()==null) || 
             (this.target_version!=null &&
              this.target_version.equals(other.getTarget_version()))) &&
            ((this.description==null && other.getDescription()==null) || 
             (this.description!=null &&
              this.description.equals(other.getDescription()))) &&
            ((this.steps_to_reproduce==null && other.getSteps_to_reproduce()==null) || 
             (this.steps_to_reproduce!=null &&
              this.steps_to_reproduce.equals(other.getSteps_to_reproduce()))) &&
            ((this.additional_information==null && other.getAdditional_information()==null) || 
             (this.additional_information!=null &&
              this.additional_information.equals(other.getAdditional_information()))) &&
            ((this.attachments==null && other.getAttachments()==null) || 
             (this.attachments!=null &&
              java.util.Arrays.equals(this.attachments, other.getAttachments()))) &&
            ((this.relationships==null && other.getRelationships()==null) || 
             (this.relationships!=null &&
              java.util.Arrays.equals(this.relationships, other.getRelationships()))) &&
            ((this.notes==null && other.getNotes()==null) || 
             (this.notes!=null &&
              java.util.Arrays.equals(this.notes, other.getNotes()))) &&
            ((this.custom_fields==null && other.getCustom_fields()==null) || 
             (this.custom_fields!=null &&
              java.util.Arrays.equals(this.custom_fields, other.getCustom_fields()))) &&
            ((this.due_date==null && other.getDue_date()==null) || 
             (this.due_date!=null &&
              this.due_date.equals(other.getDue_date()))) &&
            ((this.monitors==null && other.getMonitors()==null) || 
             (this.monitors!=null &&
              java.util.Arrays.equals(this.monitors, other.getMonitors()))) &&
            ((this.sticky==null && other.getSticky()==null) || 
             (this.sticky!=null &&
              this.sticky.equals(other.getSticky()))) &&
            ((this.tags==null && other.getTags()==null) || 
             (this.tags!=null &&
              java.util.Arrays.equals(this.tags, other.getTags())));
        __equalsCalc = null;
        return _equals;
    }

    private boolean __hashCodeCalc = false;
    public synchronized int hashCode() {
        if (__hashCodeCalc) {
            return 0;
        }
        __hashCodeCalc = true;
        int _hashCode = 1;
        if (getId() != null) {
            _hashCode += getId().hashCode();
        }
        if (getView_state() != null) {
            _hashCode += getView_state().hashCode();
        }
        if (getLast_updated() != null) {
            _hashCode += getLast_updated().hashCode();
        }
        if (getProject() != null) {
            _hashCode += getProject().hashCode();
        }
        if (getCategory() != null) {
            _hashCode += getCategory().hashCode();
        }
        if (getPriority() != null) {
            _hashCode += getPriority().hashCode();
        }
        if (getSeverity() != null) {
            _hashCode += getSeverity().hashCode();
        }
        if (getStatus() != null) {
            _hashCode += getStatus().hashCode();
        }
        if (getReporter() != null) {
            _hashCode += getReporter().hashCode();
        }
        if (getSummary() != null) {
            _hashCode += getSummary().hashCode();
        }
        if (getVersion() != null) {
            _hashCode += getVersion().hashCode();
        }
        if (getBuild() != null) {
            _hashCode += getBuild().hashCode();
        }
        if (getPlatform() != null) {
            _hashCode += getPlatform().hashCode();
        }
        if (getOs() != null) {
            _hashCode += getOs().hashCode();
        }
        if (getOs_build() != null) {
            _hashCode += getOs_build().hashCode();
        }
        if (getReproducibility() != null) {
            _hashCode += getReproducibility().hashCode();
        }
        if (getDate_submitted() != null) {
            _hashCode += getDate_submitted().hashCode();
        }
        if (getSponsorship_total() != null) {
            _hashCode += getSponsorship_total().hashCode();
        }
        if (getHandler() != null) {
            _hashCode += getHandler().hashCode();
        }
        if (getProjection() != null) {
            _hashCode += getProjection().hashCode();
        }
        if (getEta() != null) {
            _hashCode += getEta().hashCode();
        }
        if (getResolution() != null) {
            _hashCode += getResolution().hashCode();
        }
        if (getFixed_in_version() != null) {
            _hashCode += getFixed_in_version().hashCode();
        }
        if (getTarget_version() != null) {
            _hashCode += getTarget_version().hashCode();
        }
        if (getDescription() != null) {
            _hashCode += getDescription().hashCode();
        }
        if (getSteps_to_reproduce() != null) {
            _hashCode += getSteps_to_reproduce().hashCode();
        }
        if (getAdditional_information() != null) {
            _hashCode += getAdditional_information().hashCode();
        }
        if (getAttachments() != null) {
            for (int i=0;
                 i<java.lang.reflect.Array.getLength(getAttachments());
                 i++) {
                java.lang.Object obj = java.lang.reflect.Array.get(getAttachments(), i);
                if (obj != null &&
                    !obj.getClass().isArray()) {
                    _hashCode += obj.hashCode();
                }
            }
        }
        if (getRelationships() != null) {
            for (int i=0;
                 i<java.lang.reflect.Array.getLength(getRelationships());
                 i++) {
                java.lang.Object obj = java.lang.reflect.Array.get(getRelationships(), i);
                if (obj != null &&
                    !obj.getClass().isArray()) {
                    _hashCode += obj.hashCode();
                }
            }
        }
        if (getNotes() != null) {
            for (int i=0;
                 i<java.lang.reflect.Array.getLength(getNotes());
                 i++) {
                java.lang.Object obj = java.lang.reflect.Array.get(getNotes(), i);
                if (obj != null &&
                    !obj.getClass().isArray()) {
                    _hashCode += obj.hashCode();
                }
            }
        }
        if (getCustom_fields() != null) {
            for (int i=0;
                 i<java.lang.reflect.Array.getLength(getCustom_fields());
                 i++) {
                java.lang.Object obj = java.lang.reflect.Array.get(getCustom_fields(), i);
                if (obj != null &&
                    !obj.getClass().isArray()) {
                    _hashCode += obj.hashCode();
                }
            }
        }
        if (getDue_date() != null) {
            _hashCode += getDue_date().hashCode();
        }
        if (getMonitors() != null) {
            for (int i=0;
                 i<java.lang.reflect.Array.getLength(getMonitors());
                 i++) {
                java.lang.Object obj = java.lang.reflect.Array.get(getMonitors(), i);
                if (obj != null &&
                    !obj.getClass().isArray()) {
                    _hashCode += obj.hashCode();
                }
            }
        }
        if (getSticky() != null) {
            _hashCode += getSticky().hashCode();
        }
        if (getTags() != null) {
            for (int i=0;
                 i<java.lang.reflect.Array.getLength(getTags());
                 i++) {
                java.lang.Object obj = java.lang.reflect.Array.get(getTags(), i);
                if (obj != null &&
                    !obj.getClass().isArray()) {
                    _hashCode += obj.hashCode();
                }
            }
        }
        __hashCodeCalc = false;
        return _hashCode;
    }

    // Type metadata
    private static org.apache.axis.description.TypeDesc typeDesc =
        new org.apache.axis.description.TypeDesc(IssueData.class, true);

    static {
        typeDesc.setXmlType(new javax.xml.namespace.QName("http://futureware.biz/mantisconnect", "IssueData"));
        org.apache.axis.description.ElementDesc elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("id");
        elemField.setXmlName(new javax.xml.namespace.QName("", "id"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "integer"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("view_state");
        elemField.setXmlName(new javax.xml.namespace.QName("", "view_state"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://futureware.biz/mantisconnect", "ObjectRef"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("last_updated");
        elemField.setXmlName(new javax.xml.namespace.QName("", "last_updated"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "dateTime"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("project");
        elemField.setXmlName(new javax.xml.namespace.QName("", "project"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://futureware.biz/mantisconnect", "ObjectRef"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("category");
        elemField.setXmlName(new javax.xml.namespace.QName("", "category"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("priority");
        elemField.setXmlName(new javax.xml.namespace.QName("", "priority"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://futureware.biz/mantisconnect", "ObjectRef"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("severity");
        elemField.setXmlName(new javax.xml.namespace.QName("", "severity"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://futureware.biz/mantisconnect", "ObjectRef"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("status");
        elemField.setXmlName(new javax.xml.namespace.QName("", "status"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://futureware.biz/mantisconnect", "ObjectRef"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("reporter");
        elemField.setXmlName(new javax.xml.namespace.QName("", "reporter"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://futureware.biz/mantisconnect", "AccountData"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("summary");
        elemField.setXmlName(new javax.xml.namespace.QName("", "summary"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("version");
        elemField.setXmlName(new javax.xml.namespace.QName("", "version"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("build");
        elemField.setXmlName(new javax.xml.namespace.QName("", "build"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("platform");
        elemField.setXmlName(new javax.xml.namespace.QName("", "platform"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("os");
        elemField.setXmlName(new javax.xml.namespace.QName("", "os"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("os_build");
        elemField.setXmlName(new javax.xml.namespace.QName("", "os_build"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("reproducibility");
        elemField.setXmlName(new javax.xml.namespace.QName("", "reproducibility"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://futureware.biz/mantisconnect", "ObjectRef"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("date_submitted");
        elemField.setXmlName(new javax.xml.namespace.QName("", "date_submitted"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "dateTime"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("sponsorship_total");
        elemField.setXmlName(new javax.xml.namespace.QName("", "sponsorship_total"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "integer"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("handler");
        elemField.setXmlName(new javax.xml.namespace.QName("", "handler"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://futureware.biz/mantisconnect", "AccountData"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("projection");
        elemField.setXmlName(new javax.xml.namespace.QName("", "projection"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://futureware.biz/mantisconnect", "ObjectRef"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("eta");
        elemField.setXmlName(new javax.xml.namespace.QName("", "eta"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://futureware.biz/mantisconnect", "ObjectRef"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("resolution");
        elemField.setXmlName(new javax.xml.namespace.QName("", "resolution"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://futureware.biz/mantisconnect", "ObjectRef"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("fixed_in_version");
        elemField.setXmlName(new javax.xml.namespace.QName("", "fixed_in_version"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("target_version");
        elemField.setXmlName(new javax.xml.namespace.QName("", "target_version"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("description");
        elemField.setXmlName(new javax.xml.namespace.QName("", "description"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("steps_to_reproduce");
        elemField.setXmlName(new javax.xml.namespace.QName("", "steps_to_reproduce"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("additional_information");
        elemField.setXmlName(new javax.xml.namespace.QName("", "additional_information"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("attachments");
        elemField.setXmlName(new javax.xml.namespace.QName("", "attachments"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://futureware.biz/mantisconnect", "AttachmentData"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("relationships");
        elemField.setXmlName(new javax.xml.namespace.QName("", "relationships"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://futureware.biz/mantisconnect", "RelationshipData"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("notes");
        elemField.setXmlName(new javax.xml.namespace.QName("", "notes"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://futureware.biz/mantisconnect", "IssueNoteData"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("custom_fields");
        elemField.setXmlName(new javax.xml.namespace.QName("", "custom_fields"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://futureware.biz/mantisconnect", "CustomFieldValueForIssueData"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("due_date");
        elemField.setXmlName(new javax.xml.namespace.QName("", "due_date"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "dateTime"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("monitors");
        elemField.setXmlName(new javax.xml.namespace.QName("", "monitors"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://futureware.biz/mantisconnect", "AccountData"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("sticky");
        elemField.setXmlName(new javax.xml.namespace.QName("", "sticky"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "boolean"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("tags");
        elemField.setXmlName(new javax.xml.namespace.QName("", "tags"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://futureware.biz/mantisconnect", "ObjectRef"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
    }

    /**
     * Return type metadata object
     */
    public static org.apache.axis.description.TypeDesc getTypeDesc() {
        return typeDesc;
    }

    /**
     * Get Custom Serializer
     */
    public static org.apache.axis.encoding.Serializer getSerializer(
           java.lang.String mechType, 
           java.lang.Class _javaType,  
           javax.xml.namespace.QName _xmlType) {
        return 
          new  org.apache.axis.encoding.ser.BeanSerializer(
            _javaType, _xmlType, typeDesc);
    }

    /**
     * Get Custom Deserializer
     */
    public static org.apache.axis.encoding.Deserializer getDeserializer(
           java.lang.String mechType, 
           java.lang.Class _javaType,  
           javax.xml.namespace.QName _xmlType) {
        return 
          new  org.apache.axis.encoding.ser.BeanDeserializer(
            _javaType, _xmlType, typeDesc);
    }

}
