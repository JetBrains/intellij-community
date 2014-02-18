/**
 * RemoteRoleActor.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis 1.4 Apr 22, 2006 (06:55:48 PDT) WSDL2Java emitter.
 */

package com.atlassian.connector.commons.jira.soap.axis;

public class RemoteRoleActor implements java.io.Serializable {
	private String descriptor;

	private String parameter;

	private RemoteProjectRole projectRole;

	private String type;

	private RemoteUser[] users;

	public RemoteRoleActor() {
	}

	public RemoteRoleActor(
			String descriptor,
			String parameter,
			RemoteProjectRole projectRole,
			String type,
			RemoteUser[] users) {
		this.descriptor = descriptor;
		this.parameter = parameter;
		this.projectRole = projectRole;
		this.type = type;
		this.users = users;
	}


	/**
	 * Gets the descriptor value for this RemoteRoleActor.
	 *
	 * @return descriptor
	 */
	public String getDescriptor() {
		return descriptor;
	}


	/**
	 * Sets the descriptor value for this RemoteRoleActor.
	 *
	 * @param descriptor
	 */
	public void setDescriptor(String descriptor) {
		this.descriptor = descriptor;
	}


	/**
	 * Gets the parameter value for this RemoteRoleActor.
	 *
	 * @return parameter
	 */
	public String getParameter() {
		return parameter;
	}


	/**
	 * Sets the parameter value for this RemoteRoleActor.
	 *
	 * @param parameter
	 */
	public void setParameter(String parameter) {
		this.parameter = parameter;
	}


	/**
	 * Gets the projectRole value for this RemoteRoleActor.
	 *
	 * @return projectRole
	 */
	public RemoteProjectRole getProjectRole() {
		return projectRole;
	}


	/**
	 * Sets the projectRole value for this RemoteRoleActor.
	 *
	 * @param projectRole
	 */
	public void setProjectRole(RemoteProjectRole projectRole) {
		this.projectRole = projectRole;
	}


	/**
	 * Gets the type value for this RemoteRoleActor.
	 *
	 * @return type
	 */
	public String getType() {
		return type;
	}


	/**
	 * Sets the type value for this RemoteRoleActor.
	 *
	 * @param type
	 */
	public void setType(String type) {
		this.type = type;
	}


	/**
	 * Gets the users value for this RemoteRoleActor.
	 *
	 * @return users
	 */
	public RemoteUser[] getUsers() {
		return users;
	}


	/**
	 * Sets the users value for this RemoteRoleActor.
	 *
	 * @param users
	 */
	public void setUsers(RemoteUser[] users) {
		this.users = users;
	}

	private Object __equalsCalc = null;

	public synchronized boolean equals(Object obj) {
		if (!(obj instanceof RemoteRoleActor)) {
			return false;
		}
		RemoteRoleActor other = (RemoteRoleActor) obj;
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
				((this.descriptor == null && other.getDescriptor() == null) ||
						(this.descriptor != null &&
								this.descriptor.equals(other.getDescriptor()))) &&
				((this.parameter == null && other.getParameter() == null) ||
						(this.parameter != null &&
								this.parameter.equals(other.getParameter()))) &&
				((this.projectRole == null && other.getProjectRole() == null) ||
						(this.projectRole != null &&
								this.projectRole.equals(other.getProjectRole()))) &&
				((this.type == null && other.getType() == null) ||
						(this.type != null &&
								this.type.equals(other.getType()))) &&
				((this.users == null && other.getUsers() == null) ||
						(this.users != null &&
								java.util.Arrays.equals(this.users, other.getUsers())));
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
		if (getDescriptor() != null) {
			_hashCode += getDescriptor().hashCode();
		}
		if (getParameter() != null) {
			_hashCode += getParameter().hashCode();
		}
		if (getProjectRole() != null) {
			_hashCode += getProjectRole().hashCode();
		}
		if (getType() != null) {
			_hashCode += getType().hashCode();
		}
		if (getUsers() != null) {
			for (int i = 0;
				 i < java.lang.reflect.Array.getLength(getUsers());
				 i++) {
				Object obj = java.lang.reflect.Array.get(getUsers(), i);
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
			new org.apache.axis.description.TypeDesc(RemoteRoleActor.class, true);

	static {
		typeDesc.setXmlType(new javax.xml.namespace.QName("http://beans.soap.rpc.jira.atlassian.com", "RemoteRoleActor"));
		org.apache.axis.description.ElementDesc elemField = new org.apache.axis.description.ElementDesc();
		elemField.setFieldName("descriptor");
		elemField.setXmlName(new javax.xml.namespace.QName("", "descriptor"));
		elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
		elemField.setNillable(true);
		typeDesc.addFieldDesc(elemField);
		elemField = new org.apache.axis.description.ElementDesc();
		elemField.setFieldName("parameter");
		elemField.setXmlName(new javax.xml.namespace.QName("", "parameter"));
		elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
		elemField.setNillable(true);
		typeDesc.addFieldDesc(elemField);
		elemField = new org.apache.axis.description.ElementDesc();
		elemField.setFieldName("projectRole");
		elemField.setXmlName(new javax.xml.namespace.QName("", "projectRole"));
		elemField.setXmlType(new javax.xml.namespace.QName("http://beans.soap.rpc.jira.atlassian.com", "RemoteProjectRole"));
		elemField.setNillable(true);
		typeDesc.addFieldDesc(elemField);
		elemField = new org.apache.axis.description.ElementDesc();
		elemField.setFieldName("type");
		elemField.setXmlName(new javax.xml.namespace.QName("", "type"));
		elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
		elemField.setNillable(true);
		typeDesc.addFieldDesc(elemField);
		elemField = new org.apache.axis.description.ElementDesc();
		elemField.setFieldName("users");
		elemField.setXmlName(new javax.xml.namespace.QName("", "users"));
		elemField.setXmlType(new javax.xml.namespace.QName("http://beans.soap.rpc.jira.atlassian.com", "RemoteUser"));
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
