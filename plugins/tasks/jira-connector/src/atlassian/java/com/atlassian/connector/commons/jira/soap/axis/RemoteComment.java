/**
 * RemoteComment.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis 1.4 Apr 22, 2006 (06:55:48 PDT) WSDL2Java emitter.
 */

package com.atlassian.connector.commons.jira.soap.axis;

public class RemoteComment implements java.io.Serializable {
	private String author;

	private String body;

	private java.util.Calendar created;

	private String groupLevel;

	private String id;

	private String roleLevel;

	private String updateAuthor;

	private java.util.Calendar updated;

	public RemoteComment() {
	}

	public RemoteComment(
			String author,
			String body,
			java.util.Calendar created,
			String groupLevel,
			String id,
			String roleLevel,
			String updateAuthor,
			java.util.Calendar updated) {
		this.author = author;
		this.body = body;
		this.created = created;
		this.groupLevel = groupLevel;
		this.id = id;
		this.roleLevel = roleLevel;
		this.updateAuthor = updateAuthor;
		this.updated = updated;
	}


	/**
	 * Gets the author value for this RemoteComment.
	 *
	 * @return author
	 */
	public String getAuthor() {
		return author;
	}


	/**
	 * Sets the author value for this RemoteComment.
	 *
	 * @param author
	 */
	public void setAuthor(String author) {
		this.author = author;
	}


	/**
	 * Gets the body value for this RemoteComment.
	 *
	 * @return body
	 */
	public String getBody() {
		return body;
	}


	/**
	 * Sets the body value for this RemoteComment.
	 *
	 * @param body
	 */
	public void setBody(String body) {
		this.body = body;
	}


	/**
	 * Gets the created value for this RemoteComment.
	 *
	 * @return created
	 */
	public java.util.Calendar getCreated() {
		return created;
	}


	/**
	 * Sets the created value for this RemoteComment.
	 *
	 * @param created
	 */
	public void setCreated(java.util.Calendar created) {
		this.created = created;
	}


	/**
	 * Gets the groupLevel value for this RemoteComment.
	 *
	 * @return groupLevel
	 */
	public String getGroupLevel() {
		return groupLevel;
	}


	/**
	 * Sets the groupLevel value for this RemoteComment.
	 *
	 * @param groupLevel
	 */
	public void setGroupLevel(String groupLevel) {
		this.groupLevel = groupLevel;
	}


	/**
	 * Gets the id value for this RemoteComment.
	 *
	 * @return id
	 */
	public String getId() {
		return id;
	}


	/**
	 * Sets the id value for this RemoteComment.
	 *
	 * @param id
	 */
	public void setId(String id) {
		this.id = id;
	}


	/**
	 * Gets the roleLevel value for this RemoteComment.
	 *
	 * @return roleLevel
	 */
	public String getRoleLevel() {
		return roleLevel;
	}


	/**
	 * Sets the roleLevel value for this RemoteComment.
	 *
	 * @param roleLevel
	 */
	public void setRoleLevel(String roleLevel) {
		this.roleLevel = roleLevel;
	}


	/**
	 * Gets the updateAuthor value for this RemoteComment.
	 *
	 * @return updateAuthor
	 */
	public String getUpdateAuthor() {
		return updateAuthor;
	}


	/**
	 * Sets the updateAuthor value for this RemoteComment.
	 *
	 * @param updateAuthor
	 */
	public void setUpdateAuthor(String updateAuthor) {
		this.updateAuthor = updateAuthor;
	}


	/**
	 * Gets the updated value for this RemoteComment.
	 *
	 * @return updated
	 */
	public java.util.Calendar getUpdated() {
		return updated;
	}


	/**
	 * Sets the updated value for this RemoteComment.
	 *
	 * @param updated
	 */
	public void setUpdated(java.util.Calendar updated) {
		this.updated = updated;
	}

	private Object __equalsCalc = null;

	public synchronized boolean equals(Object obj) {
		if (!(obj instanceof RemoteComment)) {
			return false;
		}
		RemoteComment other = (RemoteComment) obj;
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
		_equals = true &&
				((this.author == null && other.getAuthor() == null) ||
						(this.author != null &&
								this.author.equals(other.getAuthor()))) &&
				((this.body == null && other.getBody() == null) ||
						(this.body != null &&
								this.body.equals(other.getBody()))) &&
				((this.created == null && other.getCreated() == null) ||
						(this.created != null &&
								this.created.equals(other.getCreated()))) &&
				((this.groupLevel == null && other.getGroupLevel() == null) ||
						(this.groupLevel != null &&
								this.groupLevel.equals(other.getGroupLevel()))) &&
				((this.id == null && other.getId() == null) ||
						(this.id != null &&
								this.id.equals(other.getId()))) &&
				((this.roleLevel == null && other.getRoleLevel() == null) ||
						(this.roleLevel != null &&
								this.roleLevel.equals(other.getRoleLevel()))) &&
				((this.updateAuthor == null && other.getUpdateAuthor() == null) ||
						(this.updateAuthor != null &&
								this.updateAuthor.equals(other.getUpdateAuthor()))) &&
				((this.updated == null && other.getUpdated() == null) ||
						(this.updated != null &&
								this.updated.equals(other.getUpdated())));
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
		if (getAuthor() != null) {
			_hashCode += getAuthor().hashCode();
		}
		if (getBody() != null) {
			_hashCode += getBody().hashCode();
		}
		if (getCreated() != null) {
			_hashCode += getCreated().hashCode();
		}
		if (getGroupLevel() != null) {
			_hashCode += getGroupLevel().hashCode();
		}
		if (getId() != null) {
			_hashCode += getId().hashCode();
		}
		if (getRoleLevel() != null) {
			_hashCode += getRoleLevel().hashCode();
		}
		if (getUpdateAuthor() != null) {
			_hashCode += getUpdateAuthor().hashCode();
		}
		if (getUpdated() != null) {
			_hashCode += getUpdated().hashCode();
		}
		__hashCodeCalc = false;
		return _hashCode;
	}

	// Type metadata
	private static org.apache.axis.description.TypeDesc typeDesc =
			new org.apache.axis.description.TypeDesc(RemoteComment.class, true);

	static {
		typeDesc.setXmlType(new javax.xml.namespace.QName("http://beans.soap.rpc.jira.atlassian.com", "RemoteComment"));
		org.apache.axis.description.ElementDesc elemField = new org.apache.axis.description.ElementDesc();
		elemField.setFieldName("author");
		elemField.setXmlName(new javax.xml.namespace.QName("", "author"));
		elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
		elemField.setNillable(true);
		typeDesc.addFieldDesc(elemField);
		elemField = new org.apache.axis.description.ElementDesc();
		elemField.setFieldName("body");
		elemField.setXmlName(new javax.xml.namespace.QName("", "body"));
		elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
		elemField.setNillable(true);
		typeDesc.addFieldDesc(elemField);
		elemField = new org.apache.axis.description.ElementDesc();
		elemField.setFieldName("created");
		elemField.setXmlName(new javax.xml.namespace.QName("", "created"));
		elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "dateTime"));
		elemField.setNillable(true);
		typeDesc.addFieldDesc(elemField);
		elemField = new org.apache.axis.description.ElementDesc();
		elemField.setFieldName("groupLevel");
		elemField.setXmlName(new javax.xml.namespace.QName("", "groupLevel"));
		elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
		elemField.setNillable(true);
		typeDesc.addFieldDesc(elemField);
		elemField = new org.apache.axis.description.ElementDesc();
		elemField.setFieldName("id");
		elemField.setXmlName(new javax.xml.namespace.QName("", "id"));
		elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
		elemField.setNillable(true);
		typeDesc.addFieldDesc(elemField);
		elemField = new org.apache.axis.description.ElementDesc();
		elemField.setFieldName("roleLevel");
		elemField.setXmlName(new javax.xml.namespace.QName("", "roleLevel"));
		elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
		elemField.setNillable(true);
		typeDesc.addFieldDesc(elemField);
		elemField = new org.apache.axis.description.ElementDesc();
		elemField.setFieldName("updateAuthor");
		elemField.setXmlName(new javax.xml.namespace.QName("", "updateAuthor"));
		elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
		elemField.setNillable(true);
		typeDesc.addFieldDesc(elemField);
		elemField = new org.apache.axis.description.ElementDesc();
		elemField.setFieldName("updated");
		elemField.setXmlName(new javax.xml.namespace.QName("", "updated"));
		elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "dateTime"));
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
