/**
 * IssueHeaderData.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis 1.4 Apr 22, 2006 (06:55:48 PDT) WSDL2Java emitter.
 */

package com.intellij.tasks.mantis.model;

public class IssueHeaderData  implements java.io.Serializable {
    private java.math.BigInteger id;

    private java.math.BigInteger view_state;

    private java.util.Calendar last_updated;

    private java.math.BigInteger project;

    private java.lang.String category;

    private java.math.BigInteger priority;

    private java.math.BigInteger severity;

    private java.math.BigInteger status;

    private java.math.BigInteger reporter;

    private java.lang.String summary;

    private java.math.BigInteger handler;

    private java.math.BigInteger resolution;

    private java.math.BigInteger attachments_count;

    private java.math.BigInteger notes_count;

    public IssueHeaderData() {
    }

    public IssueHeaderData(
           java.math.BigInteger id,
           java.math.BigInteger view_state,
           java.util.Calendar last_updated,
           java.math.BigInteger project,
           java.lang.String category,
           java.math.BigInteger priority,
           java.math.BigInteger severity,
           java.math.BigInteger status,
           java.math.BigInteger reporter,
           java.lang.String summary,
           java.math.BigInteger handler,
           java.math.BigInteger resolution,
           java.math.BigInteger attachments_count,
           java.math.BigInteger notes_count) {
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
           this.handler = handler;
           this.resolution = resolution;
           this.attachments_count = attachments_count;
           this.notes_count = notes_count;
    }


    /**
     * Gets the id value for this IssueHeaderData.
     * 
     * @return id
     */
    public java.math.BigInteger getId() {
        return id;
    }


    /**
     * Sets the id value for this IssueHeaderData.
     * 
     * @param id
     */
    public void setId(java.math.BigInteger id) {
        this.id = id;
    }


    /**
     * Gets the view_state value for this IssueHeaderData.
     * 
     * @return view_state
     */
    public java.math.BigInteger getView_state() {
        return view_state;
    }


    /**
     * Sets the view_state value for this IssueHeaderData.
     * 
     * @param view_state
     */
    public void setView_state(java.math.BigInteger view_state) {
        this.view_state = view_state;
    }


    /**
     * Gets the last_updated value for this IssueHeaderData.
     * 
     * @return last_updated
     */
    public java.util.Calendar getLast_updated() {
        return last_updated;
    }


    /**
     * Sets the last_updated value for this IssueHeaderData.
     * 
     * @param last_updated
     */
    public void setLast_updated(java.util.Calendar last_updated) {
        this.last_updated = last_updated;
    }


    /**
     * Gets the project value for this IssueHeaderData.
     * 
     * @return project
     */
    public java.math.BigInteger getProject() {
        return project;
    }


    /**
     * Sets the project value for this IssueHeaderData.
     * 
     * @param project
     */
    public void setProject(java.math.BigInteger project) {
        this.project = project;
    }


    /**
     * Gets the category value for this IssueHeaderData.
     * 
     * @return category
     */
    public java.lang.String getCategory() {
        return category;
    }


    /**
     * Sets the category value for this IssueHeaderData.
     * 
     * @param category
     */
    public void setCategory(java.lang.String category) {
        this.category = category;
    }


    /**
     * Gets the priority value for this IssueHeaderData.
     * 
     * @return priority
     */
    public java.math.BigInteger getPriority() {
        return priority;
    }


    /**
     * Sets the priority value for this IssueHeaderData.
     * 
     * @param priority
     */
    public void setPriority(java.math.BigInteger priority) {
        this.priority = priority;
    }


    /**
     * Gets the severity value for this IssueHeaderData.
     * 
     * @return severity
     */
    public java.math.BigInteger getSeverity() {
        return severity;
    }


    /**
     * Sets the severity value for this IssueHeaderData.
     * 
     * @param severity
     */
    public void setSeverity(java.math.BigInteger severity) {
        this.severity = severity;
    }


    /**
     * Gets the status value for this IssueHeaderData.
     * 
     * @return status
     */
    public java.math.BigInteger getStatus() {
        return status;
    }


    /**
     * Sets the status value for this IssueHeaderData.
     * 
     * @param status
     */
    public void setStatus(java.math.BigInteger status) {
        this.status = status;
    }


    /**
     * Gets the reporter value for this IssueHeaderData.
     * 
     * @return reporter
     */
    public java.math.BigInteger getReporter() {
        return reporter;
    }


    /**
     * Sets the reporter value for this IssueHeaderData.
     * 
     * @param reporter
     */
    public void setReporter(java.math.BigInteger reporter) {
        this.reporter = reporter;
    }


    /**
     * Gets the summary value for this IssueHeaderData.
     * 
     * @return summary
     */
    public java.lang.String getSummary() {
        return summary;
    }


    /**
     * Sets the summary value for this IssueHeaderData.
     * 
     * @param summary
     */
    public void setSummary(java.lang.String summary) {
        this.summary = summary;
    }


    /**
     * Gets the handler value for this IssueHeaderData.
     * 
     * @return handler
     */
    public java.math.BigInteger getHandler() {
        return handler;
    }


    /**
     * Sets the handler value for this IssueHeaderData.
     * 
     * @param handler
     */
    public void setHandler(java.math.BigInteger handler) {
        this.handler = handler;
    }


    /**
     * Gets the resolution value for this IssueHeaderData.
     * 
     * @return resolution
     */
    public java.math.BigInteger getResolution() {
        return resolution;
    }


    /**
     * Sets the resolution value for this IssueHeaderData.
     * 
     * @param resolution
     */
    public void setResolution(java.math.BigInteger resolution) {
        this.resolution = resolution;
    }


    /**
     * Gets the attachments_count value for this IssueHeaderData.
     * 
     * @return attachments_count
     */
    public java.math.BigInteger getAttachments_count() {
        return attachments_count;
    }


    /**
     * Sets the attachments_count value for this IssueHeaderData.
     * 
     * @param attachments_count
     */
    public void setAttachments_count(java.math.BigInteger attachments_count) {
        this.attachments_count = attachments_count;
    }


    /**
     * Gets the notes_count value for this IssueHeaderData.
     * 
     * @return notes_count
     */
    public java.math.BigInteger getNotes_count() {
        return notes_count;
    }


    /**
     * Sets the notes_count value for this IssueHeaderData.
     * 
     * @param notes_count
     */
    public void setNotes_count(java.math.BigInteger notes_count) {
        this.notes_count = notes_count;
    }

    private java.lang.Object __equalsCalc = null;
    public synchronized boolean equals(java.lang.Object obj) {
        if (!(obj instanceof IssueHeaderData)) return false;
        IssueHeaderData other = (IssueHeaderData) obj;
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
            ((this.handler==null && other.getHandler()==null) || 
             (this.handler!=null &&
              this.handler.equals(other.getHandler()))) &&
            ((this.resolution==null && other.getResolution()==null) || 
             (this.resolution!=null &&
              this.resolution.equals(other.getResolution()))) &&
            ((this.attachments_count==null && other.getAttachments_count()==null) || 
             (this.attachments_count!=null &&
              this.attachments_count.equals(other.getAttachments_count()))) &&
            ((this.notes_count==null && other.getNotes_count()==null) || 
             (this.notes_count!=null &&
              this.notes_count.equals(other.getNotes_count())));
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
        if (getHandler() != null) {
            _hashCode += getHandler().hashCode();
        }
        if (getResolution() != null) {
            _hashCode += getResolution().hashCode();
        }
        if (getAttachments_count() != null) {
            _hashCode += getAttachments_count().hashCode();
        }
        if (getNotes_count() != null) {
            _hashCode += getNotes_count().hashCode();
        }
        __hashCodeCalc = false;
        return _hashCode;
    }

    // Type metadata
    private static org.apache.axis.description.TypeDesc typeDesc =
        new org.apache.axis.description.TypeDesc(IssueHeaderData.class, true);

    static {
        typeDesc.setXmlType(new javax.xml.namespace.QName("http://futureware.biz/mantisconnect", "IssueHeaderData"));
        org.apache.axis.description.ElementDesc elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("id");
        elemField.setXmlName(new javax.xml.namespace.QName("", "id"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "integer"));
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("view_state");
        elemField.setXmlName(new javax.xml.namespace.QName("", "view_state"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "integer"));
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("last_updated");
        elemField.setXmlName(new javax.xml.namespace.QName("", "last_updated"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "dateTime"));
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("project");
        elemField.setXmlName(new javax.xml.namespace.QName("", "project"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "integer"));
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("category");
        elemField.setXmlName(new javax.xml.namespace.QName("", "category"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("priority");
        elemField.setXmlName(new javax.xml.namespace.QName("", "priority"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "integer"));
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("severity");
        elemField.setXmlName(new javax.xml.namespace.QName("", "severity"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "integer"));
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("status");
        elemField.setXmlName(new javax.xml.namespace.QName("", "status"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "integer"));
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("reporter");
        elemField.setXmlName(new javax.xml.namespace.QName("", "reporter"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "integer"));
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("summary");
        elemField.setXmlName(new javax.xml.namespace.QName("", "summary"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("handler");
        elemField.setXmlName(new javax.xml.namespace.QName("", "handler"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "integer"));
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("resolution");
        elemField.setXmlName(new javax.xml.namespace.QName("", "resolution"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "integer"));
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("attachments_count");
        elemField.setXmlName(new javax.xml.namespace.QName("", "attachments_count"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "integer"));
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("notes_count");
        elemField.setXmlName(new javax.xml.namespace.QName("", "notes_count"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "integer"));
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
