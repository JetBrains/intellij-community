/**
 * MantisConnectLocator.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis 1.4 Apr 22, 2006 (06:55:48 PDT) WSDL2Java emitter.
 */

package com.intellij.tasks.mantis.model;

public class MantisConnectLocator extends org.apache.axis.client.Service implements com.intellij.tasks.mantis.model.MantisConnect {

    public MantisConnectLocator() {
    }


    public MantisConnectLocator(org.apache.axis.EngineConfiguration config) {
        super(config);
    }

    public MantisConnectLocator(java.lang.String wsdlLoc, javax.xml.namespace.QName sName) throws javax.xml.rpc.ServiceException {
        super(wsdlLoc, sName);
    }

    // Use to get a proxy class for MantisConnectPort
    private java.lang.String MantisConnectPort_address = "http://localhost/mantis/api/soap/mantisconnect.php";

    public java.lang.String getMantisConnectPortAddress() {
        return MantisConnectPort_address;
    }

    // The WSDD service name defaults to the port name.
    private java.lang.String MantisConnectPortWSDDServiceName = "MantisConnectPort";

    public java.lang.String getMantisConnectPortWSDDServiceName() {
        return MantisConnectPortWSDDServiceName;
    }

    public void setMantisConnectPortWSDDServiceName(java.lang.String name) {
        MantisConnectPortWSDDServiceName = name;
    }

    public com.intellij.tasks.mantis.model.MantisConnectPortType getMantisConnectPort() throws javax.xml.rpc.ServiceException {
       java.net.URL endpoint;
        try {
            endpoint = new java.net.URL(MantisConnectPort_address);
        }
        catch (java.net.MalformedURLException e) {
            throw new javax.xml.rpc.ServiceException(e);
        }
        return getMantisConnectPort(endpoint);
    }

    public com.intellij.tasks.mantis.model.MantisConnectPortType getMantisConnectPort(java.net.URL portAddress) throws javax.xml.rpc.ServiceException {
        try {
            com.intellij.tasks.mantis.model.MantisConnectBindingStub _stub = new com.intellij.tasks.mantis.model.MantisConnectBindingStub(portAddress, this);
            _stub.setPortName(getMantisConnectPortWSDDServiceName());
            return _stub;
        }
        catch (org.apache.axis.AxisFault e) {
            return null;
        }
    }

    public void setMantisConnectPortEndpointAddress(java.lang.String address) {
        MantisConnectPort_address = address;
    }

    /**
     * For the given interface, get the stub implementation.
     * If this service has no port for the given interface,
     * then ServiceException is thrown.
     */
    public java.rmi.Remote getPort(Class serviceEndpointInterface) throws javax.xml.rpc.ServiceException {
        try {
            if (com.intellij.tasks.mantis.model.MantisConnectPortType.class.isAssignableFrom(serviceEndpointInterface)) {
                com.intellij.tasks.mantis.model.MantisConnectBindingStub _stub = new com.intellij.tasks.mantis.model.MantisConnectBindingStub(new java.net.URL(MantisConnectPort_address), this);
                _stub.setPortName(getMantisConnectPortWSDDServiceName());
                return _stub;
            }
        }
        catch (java.lang.Throwable t) {
            throw new javax.xml.rpc.ServiceException(t);
        }
        throw new javax.xml.rpc.ServiceException("There is no stub implementation for the interface:  " + (serviceEndpointInterface == null ? "null" : serviceEndpointInterface.getName()));
    }

    /**
     * For the given interface, get the stub implementation.
     * If this service has no port for the given interface,
     * then ServiceException is thrown.
     */
    public java.rmi.Remote getPort(javax.xml.namespace.QName portName, Class serviceEndpointInterface) throws javax.xml.rpc.ServiceException {
        if (portName == null) {
            return getPort(serviceEndpointInterface);
        }
        java.lang.String inputPortName = portName.getLocalPart();
        if ("MantisConnectPort".equals(inputPortName)) {
            return getMantisConnectPort();
        }
        else  {
            java.rmi.Remote _stub = getPort(serviceEndpointInterface);
            ((org.apache.axis.client.Stub) _stub).setPortName(portName);
            return _stub;
        }
    }

    public javax.xml.namespace.QName getServiceName() {
        return new javax.xml.namespace.QName("http://futureware.biz/mantisconnect", "MantisConnect");
    }

    private java.util.HashSet ports = null;

    public java.util.Iterator getPorts() {
        if (ports == null) {
            ports = new java.util.HashSet();
            ports.add(new javax.xml.namespace.QName("http://futureware.biz/mantisconnect", "MantisConnectPort"));
        }
        return ports.iterator();
    }

    /**
    * Set the endpoint address for the specified port name.
    */
    public void setEndpointAddress(java.lang.String portName, java.lang.String address) throws javax.xml.rpc.ServiceException {
        
if ("MantisConnectPort".equals(portName)) {
            setMantisConnectPortEndpointAddress(address);
        }
        else 
{ // Unknown Port Name
            throw new javax.xml.rpc.ServiceException(" Cannot set Endpoint Address for Unknown Port" + portName);
        }
    }

    /**
    * Set the endpoint address for the specified port name.
    */
    public void setEndpointAddress(javax.xml.namespace.QName portName, java.lang.String address) throws javax.xml.rpc.ServiceException {
        setEndpointAddress(portName.getLocalPart(), address);
    }

}
