/**
 * AbstractRemoteConstant.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis 1.4 Apr 22, 2006 (06:55:48 PDT) WSDL2Java emitter.
 */

package com.atlassian.connector.commons.jira.soap.axis;

public abstract class AbstractRemoteConstant extends AbstractNamedRemoteEntity
		implements java.io.Serializable {
	private String description;

	private String icon;

	public AbstractRemoteConstant() {
	}

	public AbstractRemoteConstant(
			String id,
			String name,
			String description,
			String icon) {
		super(
				id,
				name);
		this.description = description;
		this.icon = icon;
	}


	/**
	 * Gets the description value for this AbstractRemoteConstant.
	 *
	 * @return description
	 */
	public String getDescription() {
		return description;
	}


	/**
	 * Sets the description value for this AbstractRemoteConstant.
	 *
	 * @param description
	 */
	public void setDescription(String description) {
		this.description = description;
	}


	/**
	 * Gets the icon value for this AbstractRemoteConstant.
	 *
	 * @return icon
	 */
	public String getIcon() {
		return icon;
	}


	/**
	 * Sets the icon value for this AbstractRemoteConstant.
	 *
	 * @param icon
	 */
	public void setIcon(String icon) {
		this.icon = icon;
	}

	private Object __equalsCalc = null;

	public synchronized boolean equals(Object obj) {
		if (!(obj instanceof AbstractRemoteConstant)) {
			return false;
		}
		AbstractRemoteConstant other = (AbstractRemoteConstant) obj;
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
				((this.description == null && other.getDescription() == null) ||
						(this.description != null &&
								this.description.equals(other.getDescription()))) &&
				((this.icon == null && other.getIcon() == null) ||
						(this.icon != null &&
								this.icon.equals(other.getIcon())));
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
		if (getDescription() != null) {
			_hashCode += getDescription().hashCode();
		}
		if (getIcon() != null) {
			_hashCode += getIcon().hashCode();
		}
		__hashCodeCalc = false;
		return _hashCode;
	}

	// Type metadata
	private static org.apache.axis.description.TypeDesc typeDesc =
			new org.apache.axis.description.TypeDesc(AbstractRemoteConstant.class, true);

	static {
		typeDesc.setXmlType(
				new javax.xml.namespace.QName("http://beans.soap.rpc.jira.atlassian.com", "AbstractRemoteConstant"));
		org.apache.axis.description.ElementDesc elemField = new org.apache.axis.description.ElementDesc();
		elemField.setFieldName("description");
		elemField.setXmlName(new javax.xml.namespace.QName("", "description"));
		elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
		elemField.setNillable(true);
		typeDesc.addFieldDesc(elemField);
		elemField = new org.apache.axis.description.ElementDesc();
		elemField.setFieldName("icon");
		elemField.setXmlName(new javax.xml.namespace.QName("", "icon"));
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
