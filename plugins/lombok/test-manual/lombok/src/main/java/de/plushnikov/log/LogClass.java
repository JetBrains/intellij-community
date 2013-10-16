package de.plushnikov.log;

import lombok.extern.java.Log;

@Log
public class LogClass {
    private int intProperty;

    private float floatProperty;

    private String stringProperty;

    public void doSomething() {
        LogClass.log.fine("Messsdage");
    }

    public int getIntProperty() {
        LogClass.log.entering("LogClass","getIntProperty");
        return intProperty;
    }

    public void setIntProperty(int intProperty) {
        this.intProperty = intProperty;
    }
}
