/**
 * RemoteServerInfo.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis 1.4 Apr 22, 2006 (06:55:48 PDT) WSDL2Java emitter.
 */

package com.atlassian.connector.commons.jira.soap.axis;

public class RemoteServerInfo implements java.io.Serializable {
	private String baseUrl;

	private java.util.Calendar buildDate;

	private String buildNumber;

	private String edition;

	private RemoteTimeInfo serverTime;

	private String version;

	public RemoteServerInfo() {
	}

	public RemoteServerInfo(
			String baseUrl,
			java.util.Calendar buildDate,
			String buildNumber,
			String edition,
			RemoteTimeInfo serverTime,
			String version) {
		this.baseUrl = baseUrl;
		this.buildDate = buildDate;
		this.buildNumber = buildNumber;
		this.edition = edition;
		this.serverTime = serverTime;
		this.version = version;
	}


	/**
	 * Gets the baseUrl value for this RemoteServerInfo.
	 *
	 * @return baseUrl
	 */
	public String getBaseUrl() {
		return baseUrl;
	}


	/**
	 * Sets the baseUrl value for this RemoteServerInfo.
	 *
	 * @param baseUrl
	 */
	public void setBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl;
	}


	/**
	 * Gets the buildDate value for this RemoteServerInfo.
	 *
	 * @return buildDate
	 */
	public java.util.Calendar getBuildDate() {
		return buildDate;
	}


	/**
	 * Sets the buildDate value for this RemoteServerInfo.
	 *
	 * @param buildDate
	 */
	public void setBuildDate(java.util.Calendar buildDate) {
		this.buildDate = buildDate;
	}


	/**
	 * Gets the buildNumber value for this RemoteServerInfo.
	 *
	 * @return buildNumber
	 */
	public String getBuildNumber() {
		return buildNumber;
	}


	/**
	 * Sets the buildNumber value for this RemoteServerInfo.
	 *
	 * @param buildNumber
	 */
	public void setBuildNumber(String buildNumber) {
		this.buildNumber = buildNumber;
	}


	/**
	 * Gets the edition value for this RemoteServerInfo.
	 *
	 * @return edition
	 */
	public String getEdition() {
		return edition;
	}


	/**
	 * Sets the edition value for this RemoteServerInfo.
	 *
	 * @param edition
	 */
	public void setEdition(String edition) {
		this.edition = edition;
	}


	/**
	 * Gets the serverTime value for this RemoteServerInfo.
	 *
	 * @return serverTime
	 */
	public RemoteTimeInfo getServerTime() {
		return serverTime;
	}


	/**
	 * Sets the serverTime value for this RemoteServerInfo.
	 *
	 * @param serverTime
	 */
	public void setServerTime(RemoteTimeInfo serverTime) {
		this.serverTime = serverTime;
	}


	/**
	 * Gets the version value for this RemoteServerInfo.
	 *
	 * @return version
	 */
	public String getVersion() {
		return version;
	}


	/**
	 * Sets the version value for this RemoteServerInfo.
	 *
	 * @param version
	 */
	public void setVersion(String version) {
		this.version = version;
	}

	private Object __equalsCalc = null;

	public synchronized boolean equals(Object obj) {
		if (!(obj instanceof RemoteServerInfo)) {
			return false;
		}
		RemoteServerInfo other = (RemoteServerInfo) obj;
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
				((this.baseUrl == null && other.getBaseUrl() == null) ||
						(this.baseUrl != null &&
								this.baseUrl.equals(other.getBaseUrl()))) &&
				((this.buildDate == null && other.getBuildDate() == null) ||
						(this.buildDate != null &&
								this.buildDate.equals(other.getBuildDate()))) &&
				((this.buildNumber == null && other.getBuildNumber() == null) ||
						(this.buildNumber != null &&
								this.buildNumber.equals(other.getBuildNumber()))) &&
				((this.edition == null && other.getEdition() == null) ||
						(this.edition != null &&
								this.edition.equals(other.getEdition()))) &&
				((this.serverTime == null && other.getServerTime() == null) ||
						(this.serverTime != null &&
								this.serverTime.equals(other.getServerTime()))) &&
				((this.version == null && other.getVersion() == null) ||
						(this.version != null &&
								this.version.equals(other.getVersion())));
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
		if (getBaseUrl() != null) {
			_hashCode += getBaseUrl().hashCode();
		}
		if (getBuildDate() != null) {
			_hashCode += getBuildDate().hashCode();
		}
		if (getBuildNumber() != null) {
			_hashCode += getBuildNumber().hashCode();
		}
		if (getEdition() != null) {
			_hashCode += getEdition().hashCode();
		}
		if (getServerTime() != null) {
			_hashCode += getServerTime().hashCode();
		}
		if (getVersion() != null) {
			_hashCode += getVersion().hashCode();
		}
		__hashCodeCalc = false;
		return _hashCode;
	}

	// Type metadata
	private static org.apache.axis.description.TypeDesc typeDesc =
			new org.apache.axis.description.TypeDesc(RemoteServerInfo.class, true);

	static {
		typeDesc.setXmlType(new javax.xml.namespace.QName("http://beans.soap.rpc.jira.atlassian.com", "RemoteServerInfo"));
		org.apache.axis.description.ElementDesc elemField = new org.apache.axis.description.ElementDesc();
		elemField.setFieldName("baseUrl");
		elemField.setXmlName(new javax.xml.namespace.QName("", "baseUrl"));
		elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
		elemField.setNillable(true);
		typeDesc.addFieldDesc(elemField);
		elemField = new org.apache.axis.description.ElementDesc();
		elemField.setFieldName("buildDate");
		elemField.setXmlName(new javax.xml.namespace.QName("", "buildDate"));
		elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "dateTime"));
		elemField.setNillable(true);
		typeDesc.addFieldDesc(elemField);
		elemField = new org.apache.axis.description.ElementDesc();
		elemField.setFieldName("buildNumber");
		elemField.setXmlName(new javax.xml.namespace.QName("", "buildNumber"));
		elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
		elemField.setNillable(true);
		typeDesc.addFieldDesc(elemField);
		elemField = new org.apache.axis.description.ElementDesc();
		elemField.setFieldName("edition");
		elemField.setXmlName(new javax.xml.namespace.QName("", "edition"));
		elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
		elemField.setNillable(true);
		typeDesc.addFieldDesc(elemField);
		elemField = new org.apache.axis.description.ElementDesc();
		elemField.setFieldName("serverTime");
		elemField.setXmlName(new javax.xml.namespace.QName("", "serverTime"));
		elemField.setXmlType(new javax.xml.namespace.QName("http://beans.soap.rpc.jira.atlassian.com", "RemoteTimeInfo"));
		elemField.setNillable(true);
		typeDesc.addFieldDesc(elemField);
		elemField = new org.apache.axis.description.ElementDesc();
		elemField.setFieldName("version");
		elemField.setXmlName(new javax.xml.namespace.QName("", "version"));
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
