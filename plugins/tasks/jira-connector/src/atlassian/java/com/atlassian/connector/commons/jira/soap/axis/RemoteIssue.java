/**
 * RemoteIssue.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis 1.4 Apr 22, 2006 (06:55:48 PDT) WSDL2Java emitter.
 */

package com.atlassian.connector.commons.jira.soap.axis;

public class RemoteIssue extends AbstractRemoteEntity
		implements java.io.Serializable {
	private RemoteVersion[] affectsVersions;

	private String assignee;

	private String[] attachmentNames;

	private RemoteComponent[] components;

	private java.util.Calendar created;

	private RemoteCustomFieldValue[] customFieldValues;

	private String description;

	private java.util.Calendar duedate;

	private String environment;

	private RemoteVersion[] fixVersions;

	private String key;

	private String priority;

	private String project;

	private String reporter;

	private String resolution;

	private String status;

	private String summary;

	private String type;

	private java.util.Calendar updated;

	private Long votes;

	public RemoteIssue() {
	}

	public RemoteIssue(
			String id,
			RemoteVersion[] affectsVersions,
			String assignee,
			String[] attachmentNames,
			RemoteComponent[] components,
			java.util.Calendar created,
			RemoteCustomFieldValue[] customFieldValues,
			String description,
			java.util.Calendar duedate,
			String environment,
			RemoteVersion[] fixVersions,
			String key,
			String priority,
			String project,
			String reporter,
			String resolution,
			String status,
			String summary,
			String type,
			java.util.Calendar updated,
			Long votes) {
		super(
				id);
		this.affectsVersions = affectsVersions;
		this.assignee = assignee;
		this.attachmentNames = attachmentNames;
		this.components = components;
		this.created = created;
		this.customFieldValues = customFieldValues;
		this.description = description;
		this.duedate = duedate;
		this.environment = environment;
		this.fixVersions = fixVersions;
		this.key = key;
		this.priority = priority;
		this.project = project;
		this.reporter = reporter;
		this.resolution = resolution;
		this.status = status;
		this.summary = summary;
		this.type = type;
		this.updated = updated;
		this.votes = votes;
	}


	/**
	 * Gets the affectsVersions value for this RemoteIssue.
	 *
	 * @return affectsVersions
	 */
	public RemoteVersion[] getAffectsVersions() {
		return affectsVersions;
	}


	/**
	 * Sets the affectsVersions value for this RemoteIssue.
	 *
	 * @param affectsVersions
	 */
	public void setAffectsVersions(RemoteVersion[] affectsVersions) {
		this.affectsVersions = affectsVersions;
	}


	/**
	 * Gets the assignee value for this RemoteIssue.
	 *
	 * @return assignee
	 */
	public String getAssignee() {
		return assignee;
	}


	/**
	 * Sets the assignee value for this RemoteIssue.
	 *
	 * @param assignee
	 */
	public void setAssignee(String assignee) {
		this.assignee = assignee;
	}


	/**
	 * Gets the attachmentNames value for this RemoteIssue.
	 *
	 * @return attachmentNames
	 */
	public String[] getAttachmentNames() {
		return attachmentNames;
	}


	/**
	 * Sets the attachmentNames value for this RemoteIssue.
	 *
	 * @param attachmentNames
	 */
	public void setAttachmentNames(String[] attachmentNames) {
		this.attachmentNames = attachmentNames;
	}


	/**
	 * Gets the components value for this RemoteIssue.
	 *
	 * @return components
	 */
	public RemoteComponent[] getComponents() {
		return components;
	}


	/**
	 * Sets the components value for this RemoteIssue.
	 *
	 * @param components
	 */
	public void setComponents(RemoteComponent[] components) {
		this.components = components;
	}


	/**
	 * Gets the created value for this RemoteIssue.
	 *
	 * @return created
	 */
	public java.util.Calendar getCreated() {
		return created;
	}


	/**
	 * Sets the created value for this RemoteIssue.
	 *
	 * @param created
	 */
	public void setCreated(java.util.Calendar created) {
		this.created = created;
	}


	/**
	 * Gets the customFieldValues value for this RemoteIssue.
	 *
	 * @return customFieldValues
	 */
	public RemoteCustomFieldValue[] getCustomFieldValues() {
		return customFieldValues;
	}


	/**
	 * Sets the customFieldValues value for this RemoteIssue.
	 *
	 * @param customFieldValues
	 */
	public void setCustomFieldValues(RemoteCustomFieldValue[] customFieldValues) {
		this.customFieldValues = customFieldValues;
	}


	/**
	 * Gets the description value for this RemoteIssue.
	 *
	 * @return description
	 */
	public String getDescription() {
		return description;
	}


	/**
	 * Sets the description value for this RemoteIssue.
	 *
	 * @param description
	 */
	public void setDescription(String description) {
		this.description = description;
	}


	/**
	 * Gets the duedate value for this RemoteIssue.
	 *
	 * @return duedate
	 */
	public java.util.Calendar getDuedate() {
		return duedate;
	}


	/**
	 * Sets the duedate value for this RemoteIssue.
	 *
	 * @param duedate
	 */
	public void setDuedate(java.util.Calendar duedate) {
		this.duedate = duedate;
	}


	/**
	 * Gets the environment value for this RemoteIssue.
	 *
	 * @return environment
	 */
	public String getEnvironment() {
		return environment;
	}


	/**
	 * Sets the environment value for this RemoteIssue.
	 *
	 * @param environment
	 */
	public void setEnvironment(String environment) {
		this.environment = environment;
	}


	/**
	 * Gets the fixVersions value for this RemoteIssue.
	 *
	 * @return fixVersions
	 */
	public RemoteVersion[] getFixVersions() {
		return fixVersions;
	}


	/**
	 * Sets the fixVersions value for this RemoteIssue.
	 *
	 * @param fixVersions
	 */
	public void setFixVersions(RemoteVersion[] fixVersions) {
		this.fixVersions = fixVersions;
	}


	/**
	 * Gets the key value for this RemoteIssue.
	 *
	 * @return key
	 */
	public String getKey() {
		return key;
	}


	/**
	 * Sets the key value for this RemoteIssue.
	 *
	 * @param key
	 */
	public void setKey(String key) {
		this.key = key;
	}


	/**
	 * Gets the priority value for this RemoteIssue.
	 *
	 * @return priority
	 */
	public String getPriority() {
		return priority;
	}


	/**
	 * Sets the priority value for this RemoteIssue.
	 *
	 * @param priority
	 */
	public void setPriority(String priority) {
		this.priority = priority;
	}


	/**
	 * Gets the project value for this RemoteIssue.
	 *
	 * @return project
	 */
	public String getProject() {
		return project;
	}


	/**
	 * Sets the project value for this RemoteIssue.
	 *
	 * @param project
	 */
	public void setProject(String project) {
		this.project = project;
	}


	/**
	 * Gets the reporter value for this RemoteIssue.
	 *
	 * @return reporter
	 */
	public String getReporter() {
		return reporter;
	}


	/**
	 * Sets the reporter value for this RemoteIssue.
	 *
	 * @param reporter
	 */
	public void setReporter(String reporter) {
		this.reporter = reporter;
	}


	/**
	 * Gets the resolution value for this RemoteIssue.
	 *
	 * @return resolution
	 */
	public String getResolution() {
		return resolution;
	}


	/**
	 * Sets the resolution value for this RemoteIssue.
	 *
	 * @param resolution
	 */
	public void setResolution(String resolution) {
		this.resolution = resolution;
	}


	/**
	 * Gets the status value for this RemoteIssue.
	 *
	 * @return status
	 */
	public String getStatus() {
		return status;
	}


	/**
	 * Sets the status value for this RemoteIssue.
	 *
	 * @param status
	 */
	public void setStatus(String status) {
		this.status = status;
	}


	/**
	 * Gets the summary value for this RemoteIssue.
	 *
	 * @return summary
	 */
	public String getSummary() {
		return summary;
	}


	/**
	 * Sets the summary value for this RemoteIssue.
	 *
	 * @param summary
	 */
	public void setSummary(String summary) {
		this.summary = summary;
	}


	/**
	 * Gets the type value for this RemoteIssue.
	 *
	 * @return type
	 */
	public String getType() {
		return type;
	}


	/**
	 * Sets the type value for this RemoteIssue.
	 *
	 * @param type
	 */
	public void setType(String type) {
		this.type = type;
	}


	/**
	 * Gets the updated value for this RemoteIssue.
	 *
	 * @return updated
	 */
	public java.util.Calendar getUpdated() {
		return updated;
	}


	/**
	 * Sets the updated value for this RemoteIssue.
	 *
	 * @param updated
	 */
	public void setUpdated(java.util.Calendar updated) {
		this.updated = updated;
	}


	/**
	 * Gets the votes value for this RemoteIssue.
	 *
	 * @return votes
	 */
	public Long getVotes() {
		return votes;
	}


	/**
	 * Sets the votes value for this RemoteIssue.
	 *
	 * @param votes
	 */
	public void setVotes(Long votes) {
		this.votes = votes;
	}

	private Object __equalsCalc = null;

	public synchronized boolean equals(Object obj) {
		if (!(obj instanceof RemoteIssue)) {
			return false;
		}
		RemoteIssue other = (RemoteIssue) obj;
		if (obj == null) {
			return false;
		}
		if (this == obj) {
			return true;
		}
		if (__equalsCalc != null) {
			return (__equalsCalc == obj);
		}
		__equalsCalc = obj;
		boolean _equals;
		_equals = super.equals(obj) &&
				((this.affectsVersions == null && other.getAffectsVersions() == null) ||
						(this.affectsVersions != null &&
								java.util.Arrays.equals(this.affectsVersions, other.getAffectsVersions()))) &&
				((this.assignee == null && other.getAssignee() == null) ||
						(this.assignee != null &&
								this.assignee.equals(other.getAssignee()))) &&
				((this.attachmentNames == null && other.getAttachmentNames() == null) ||
						(this.attachmentNames != null &&
								java.util.Arrays.equals(this.attachmentNames, other.getAttachmentNames()))) &&
				((this.components == null && other.getComponents() == null) ||
						(this.components != null &&
								java.util.Arrays.equals(this.components, other.getComponents()))) &&
				((this.created == null && other.getCreated() == null) ||
						(this.created != null &&
								this.created.equals(other.getCreated()))) &&
				((this.customFieldValues == null && other.getCustomFieldValues() == null) ||
						(this.customFieldValues != null &&
								java.util.Arrays.equals(this.customFieldValues, other.getCustomFieldValues()))) &&
				((this.description == null && other.getDescription() == null) ||
						(this.description != null &&
								this.description.equals(other.getDescription()))) &&
				((this.duedate == null && other.getDuedate() == null) ||
						(this.duedate != null &&
								this.duedate.equals(other.getDuedate()))) &&
				((this.environment == null && other.getEnvironment() == null) ||
						(this.environment != null &&
								this.environment.equals(other.getEnvironment()))) &&
				((this.fixVersions == null && other.getFixVersions() == null) ||
						(this.fixVersions != null &&
								java.util.Arrays.equals(this.fixVersions, other.getFixVersions()))) &&
				((this.key == null && other.getKey() == null) ||
						(this.key != null &&
								this.key.equals(other.getKey()))) &&
				((this.priority == null && other.getPriority() == null) ||
						(this.priority != null &&
								this.priority.equals(other.getPriority()))) &&
				((this.project == null && other.getProject() == null) ||
						(this.project != null &&
								this.project.equals(other.getProject()))) &&
				((this.reporter == null && other.getReporter() == null) ||
						(this.reporter != null &&
								this.reporter.equals(other.getReporter()))) &&
				((this.resolution == null && other.getResolution() == null) ||
						(this.resolution != null &&
								this.resolution.equals(other.getResolution()))) &&
				((this.status == null && other.getStatus() == null) ||
						(this.status != null &&
								this.status.equals(other.getStatus()))) &&
				((this.summary == null && other.getSummary() == null) ||
						(this.summary != null &&
								this.summary.equals(other.getSummary()))) &&
				((this.type == null && other.getType() == null) ||
						(this.type != null &&
								this.type.equals(other.getType()))) &&
				((this.updated == null && other.getUpdated() == null) ||
						(this.updated != null &&
								this.updated.equals(other.getUpdated()))) &&
				((this.votes == null && other.getVotes() == null) ||
						(this.votes != null &&
								this.votes.equals(other.getVotes())));
		__equalsCalc = null;
		return _equals;
	}

	private boolean __hashCodeCalc = false;

	public synchronized int hashCode() {
		if (__hashCodeCalc) {
			return 0;
		}
		__hashCodeCalc = true;
		int _hashCode = super.hashCode();
		if (getAffectsVersions() != null) {
			for (int i = 0;
				 i < java.lang.reflect.Array.getLength(getAffectsVersions());
				 i++) {
				Object obj = java.lang.reflect.Array.get(getAffectsVersions(), i);
				if (obj != null &&
						!obj.getClass().isArray()) {
					_hashCode += obj.hashCode();
				}
			}
		}
		if (getAssignee() != null) {
			_hashCode += getAssignee().hashCode();
		}
		if (getAttachmentNames() != null) {
			for (int i = 0;
				 i < java.lang.reflect.Array.getLength(getAttachmentNames());
				 i++) {
				Object obj = java.lang.reflect.Array.get(getAttachmentNames(), i);
				if (obj != null &&
						!obj.getClass().isArray()) {
					_hashCode += obj.hashCode();
				}
			}
		}
		if (getComponents() != null) {
			for (int i = 0;
				 i < java.lang.reflect.Array.getLength(getComponents());
				 i++) {
				Object obj = java.lang.reflect.Array.get(getComponents(), i);
				if (obj != null &&
						!obj.getClass().isArray()) {
					_hashCode += obj.hashCode();
				}
			}
		}
		if (getCreated() != null) {
			_hashCode += getCreated().hashCode();
		}
		if (getCustomFieldValues() != null) {
			for (int i = 0;
				 i < java.lang.reflect.Array.getLength(getCustomFieldValues());
				 i++) {
				Object obj = java.lang.reflect.Array.get(getCustomFieldValues(), i);
				if (obj != null &&
						!obj.getClass().isArray()) {
					_hashCode += obj.hashCode();
				}
			}
		}
		if (getDescription() != null) {
			_hashCode += getDescription().hashCode();
		}
		if (getDuedate() != null) {
			_hashCode += getDuedate().hashCode();
		}
		if (getEnvironment() != null) {
			_hashCode += getEnvironment().hashCode();
		}
		if (getFixVersions() != null) {
			for (int i = 0;
				 i < java.lang.reflect.Array.getLength(getFixVersions());
				 i++) {
				Object obj = java.lang.reflect.Array.get(getFixVersions(), i);
				if (obj != null &&
						!obj.getClass().isArray()) {
					_hashCode += obj.hashCode();
				}
			}
		}
		if (getKey() != null) {
			_hashCode += getKey().hashCode();
		}
		if (getPriority() != null) {
			_hashCode += getPriority().hashCode();
		}
		if (getProject() != null) {
			_hashCode += getProject().hashCode();
		}
		if (getReporter() != null) {
			_hashCode += getReporter().hashCode();
		}
		if (getResolution() != null) {
			_hashCode += getResolution().hashCode();
		}
		if (getStatus() != null) {
			_hashCode += getStatus().hashCode();
		}
		if (getSummary() != null) {
			_hashCode += getSummary().hashCode();
		}
		if (getType() != null) {
			_hashCode += getType().hashCode();
		}
		if (getUpdated() != null) {
			_hashCode += getUpdated().hashCode();
		}
		if (getVotes() != null) {
			_hashCode += getVotes().hashCode();
		}
		__hashCodeCalc = false;
		return _hashCode;
	}

	// Type metadata
	private static org.apache.axis.description.TypeDesc typeDesc =
			new org.apache.axis.description.TypeDesc(RemoteIssue.class, true);

	static {
		typeDesc.setXmlType(new javax.xml.namespace.QName("http://beans.soap.rpc.jira.atlassian.com", "RemoteIssue"));
		org.apache.axis.description.ElementDesc elemField = new org.apache.axis.description.ElementDesc();
		elemField.setFieldName("affectsVersions");
		elemField.setXmlName(new javax.xml.namespace.QName("", "affectsVersions"));
		elemField.setXmlType(new javax.xml.namespace.QName("http://beans.soap.rpc.jira.atlassian.com", "RemoteVersion"));
		elemField.setNillable(true);
		typeDesc.addFieldDesc(elemField);
		elemField = new org.apache.axis.description.ElementDesc();
		elemField.setFieldName("assignee");
		elemField.setXmlName(new javax.xml.namespace.QName("", "assignee"));
		elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
		elemField.setNillable(true);
		typeDesc.addFieldDesc(elemField);
		elemField = new org.apache.axis.description.ElementDesc();
		elemField.setFieldName("attachmentNames");
		elemField.setXmlName(new javax.xml.namespace.QName("", "attachmentNames"));
		elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
		elemField.setNillable(true);
		typeDesc.addFieldDesc(elemField);
		elemField = new org.apache.axis.description.ElementDesc();
		elemField.setFieldName("components");
		elemField.setXmlName(new javax.xml.namespace.QName("", "components"));
		elemField.setXmlType(new javax.xml.namespace.QName("http://beans.soap.rpc.jira.atlassian.com", "RemoteComponent"));
		elemField.setNillable(true);
		typeDesc.addFieldDesc(elemField);
		elemField = new org.apache.axis.description.ElementDesc();
		elemField.setFieldName("created");
		elemField.setXmlName(new javax.xml.namespace.QName("", "created"));
		elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "dateTime"));
		elemField.setNillable(true);
		typeDesc.addFieldDesc(elemField);
		elemField = new org.apache.axis.description.ElementDesc();
		elemField.setFieldName("customFieldValues");
		elemField.setXmlName(new javax.xml.namespace.QName("", "customFieldValues"));
		elemField.setXmlType(
				new javax.xml.namespace.QName("http://beans.soap.rpc.jira.atlassian.com", "RemoteCustomFieldValue"));
		elemField.setNillable(true);
		typeDesc.addFieldDesc(elemField);
		elemField = new org.apache.axis.description.ElementDesc();
		elemField.setFieldName("description");
		elemField.setXmlName(new javax.xml.namespace.QName("", "description"));
		elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
		elemField.setNillable(true);
		typeDesc.addFieldDesc(elemField);
		elemField = new org.apache.axis.description.ElementDesc();
		elemField.setFieldName("duedate");
		elemField.setXmlName(new javax.xml.namespace.QName("", "duedate"));
		elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "dateTime"));
		elemField.setNillable(true);
		typeDesc.addFieldDesc(elemField);
		elemField = new org.apache.axis.description.ElementDesc();
		elemField.setFieldName("environment");
		elemField.setXmlName(new javax.xml.namespace.QName("", "environment"));
		elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
		elemField.setNillable(true);
		typeDesc.addFieldDesc(elemField);
		elemField = new org.apache.axis.description.ElementDesc();
		elemField.setFieldName("fixVersions");
		elemField.setXmlName(new javax.xml.namespace.QName("", "fixVersions"));
		elemField.setXmlType(new javax.xml.namespace.QName("http://beans.soap.rpc.jira.atlassian.com", "RemoteVersion"));
		elemField.setNillable(true);
		typeDesc.addFieldDesc(elemField);
		elemField = new org.apache.axis.description.ElementDesc();
		elemField.setFieldName("key");
		elemField.setXmlName(new javax.xml.namespace.QName("", "key"));
		elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
		elemField.setNillable(true);
		typeDesc.addFieldDesc(elemField);
		elemField = new org.apache.axis.description.ElementDesc();
		elemField.setFieldName("priority");
		elemField.setXmlName(new javax.xml.namespace.QName("", "priority"));
		elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
		elemField.setNillable(true);
		typeDesc.addFieldDesc(elemField);
		elemField = new org.apache.axis.description.ElementDesc();
		elemField.setFieldName("project");
		elemField.setXmlName(new javax.xml.namespace.QName("", "project"));
		elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
		elemField.setNillable(true);
		typeDesc.addFieldDesc(elemField);
		elemField = new org.apache.axis.description.ElementDesc();
		elemField.setFieldName("reporter");
		elemField.setXmlName(new javax.xml.namespace.QName("", "reporter"));
		elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
		elemField.setNillable(true);
		typeDesc.addFieldDesc(elemField);
		elemField = new org.apache.axis.description.ElementDesc();
		elemField.setFieldName("resolution");
		elemField.setXmlName(new javax.xml.namespace.QName("", "resolution"));
		elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
		elemField.setNillable(true);
		typeDesc.addFieldDesc(elemField);
		elemField = new org.apache.axis.description.ElementDesc();
		elemField.setFieldName("status");
		elemField.setXmlName(new javax.xml.namespace.QName("", "status"));
		elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
		elemField.setNillable(true);
		typeDesc.addFieldDesc(elemField);
		elemField = new org.apache.axis.description.ElementDesc();
		elemField.setFieldName("summary");
		elemField.setXmlName(new javax.xml.namespace.QName("", "summary"));
		elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
		elemField.setNillable(true);
		typeDesc.addFieldDesc(elemField);
		elemField = new org.apache.axis.description.ElementDesc();
		elemField.setFieldName("type");
		elemField.setXmlName(new javax.xml.namespace.QName("", "type"));
		elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
		elemField.setNillable(true);
		typeDesc.addFieldDesc(elemField);
		elemField = new org.apache.axis.description.ElementDesc();
		elemField.setFieldName("updated");
		elemField.setXmlName(new javax.xml.namespace.QName("", "updated"));
		elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "dateTime"));
		elemField.setNillable(true);
		typeDesc.addFieldDesc(elemField);
		elemField = new org.apache.axis.description.ElementDesc();
		elemField.setFieldName("votes");
		elemField.setXmlName(new javax.xml.namespace.QName("", "votes"));
		elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "long"));
		elemField.setNillable(true);
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
			String mechType,
			Class _javaType,
			javax.xml.namespace.QName _xmlType) {
		return
				new org.apache.axis.encoding.ser.BeanSerializer(
						_javaType, _xmlType, typeDesc);
	}

	/**
	 * Get Custom Deserializer
	 */
	public static org.apache.axis.encoding.Deserializer getDeserializer(
			String mechType,
			Class _javaType,
			javax.xml.namespace.QName _xmlType) {
		return
				new org.apache.axis.encoding.ser.BeanDeserializer(
						_javaType, _xmlType, typeDesc);
	}

}
