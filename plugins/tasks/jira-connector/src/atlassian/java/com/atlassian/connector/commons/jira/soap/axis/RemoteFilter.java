/**
 * RemoteFilter.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis 1.4 Apr 22, 2006 (06:55:48 PDT) WSDL2Java emitter.
 */

package com.atlassian.connector.commons.jira.soap.axis;

public class RemoteFilter extends AbstractNamedRemoteEntity
		implements java.io.Serializable {
	private String author;

	private String description;

	private String project;

	private String xml;

	public RemoteFilter() {
	}

	public RemoteFilter(
			String id,
			String name,
			String author,
			String description,
			String project,
			String xml) {
		super(
				id,
				name);
		this.author = author;
		this.description = description;
		this.project = project;
		this.xml = xml;
	}


	/**
	 * Gets the author value for this RemoteFilter.
	 *
	 * @return author
	 */
	public String getAuthor() {
		return author;
	}


	/**
	 * Sets the author value for this RemoteFilter.
	 *
	 * @param author
	 */
	public void setAuthor(String author) {
		this.author = author;
	}


	/**
	 * Gets the description value for this RemoteFilter.
	 *
	 * @return description
	 */
	public String getDescription() {
		return description;
	}


	/**
	 * Sets the description value for this RemoteFilter.
	 *
	 * @param description
	 */
	public void setDescription(String description) {
		this.description = description;
	}


	/**
	 * Gets the project value for this RemoteFilter.
	 *
	 * @return project
	 */
	public String getProject() {
		return project;
	}


	/**
	 * Sets the project value for this RemoteFilter.
	 *
	 * @param project
	 */
	public void setProject(String project) {
		this.project = project;
	}


	/**
	 * Gets the xml value for this RemoteFilter.
	 *
	 * @return xml
	 */
	public String getXml() {
		return xml;
	}


	/**
	 * Sets the xml value for this RemoteFilter.
	 *
	 * @param xml
	 */
	public void setXml(String xml) {
		this.xml = xml;
	}

	private Object __equalsCalc = null;

	public synchronized boolean equals(Object obj) {
		if (!(obj instanceof RemoteFilter)) {
			return false;
		}
		RemoteFilter other = (RemoteFilter) obj;
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
				((this.author == null && other.getAuthor() == null) ||
						(this.author != null &&
								this.author.equals(other.getAuthor()))) &&
				((this.description == null && other.getDescription() == null) ||
						(this.description != null &&
								this.description.equals(other.getDescription()))) &&
				((this.project == null && other.getProject() == null) ||
						(this.project != null &&
								this.project.equals(other.getProject()))) &&
				((this.xml == null && other.getXml() == null) ||
						(this.xml != null &&
								this.xml.equals(other.getXml())));
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
		if (getAuthor() != null) {
			_hashCode += getAuthor().hashCode();
		}
		if (getDescription() != null) {
			_hashCode += getDescription().hashCode();
		}
		if (getProject() != null) {
			_hashCode += getProject().hashCode();
		}
		if (getXml() != null) {
			_hashCode += getXml().hashCode();
		}
		__hashCodeCalc = false;
		return _hashCode;
	}

	// Type metadata
	private static org.apache.axis.description.TypeDesc typeDesc =
			new org.apache.axis.description.TypeDesc(RemoteFilter.class, true);

	static {
		typeDesc.setXmlType(new javax.xml.namespace.QName("http://beans.soap.rpc.jira.atlassian.com", "RemoteFilter"));
		org.apache.axis.description.ElementDesc elemField = new org.apache.axis.description.ElementDesc();
		elemField.setFieldName("author");
		elemField.setXmlName(new javax.xml.namespace.QName("", "author"));
		elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
		elemField.setNillable(true);
		typeDesc.addFieldDesc(elemField);
		elemField = new org.apache.axis.description.ElementDesc();
		elemField.setFieldName("description");
		elemField.setXmlName(new javax.xml.namespace.QName("", "description"));
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
		elemField.setFieldName("xml");
		elemField.setXmlName(new javax.xml.namespace.QName("", "xml"));
		elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
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
