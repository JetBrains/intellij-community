class SetterOnBooleanIsPrefixedField {
    private boolean isUri;

    public void setIsUri(boolean isUri) {
        this.isUri = isUri;
    }
}

class <warning descr="Class 'SetterOnValidBooleanIsPrefixedField' may use Lombok @Setter">SetterOnValidBooleanIsPrefixedField</warning> {
    private boolean isUri;

    public void setUri(boolean isUri) {
        this.isUri = isUri;
    }
}

class <warning descr="Class 'SetterOnIsPrefixedField' may use Lombok @Setter">SetterOnIsPrefixedField</warning> {
    private String isUri;

    public void setIsUri(String isUri) {
        this.isUri = isUri;
    }
}

