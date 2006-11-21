package com.intellij.util.xmlb;

public class XmlSerializationException extends Exception {

    public XmlSerializationException() {
    }

    public XmlSerializationException(String message) {
        super(message);
    }

    public XmlSerializationException(String message, Throwable cause) {
        super(message, cause);
    }

    public XmlSerializationException(Throwable cause) {
        super(cause);
    }
}
