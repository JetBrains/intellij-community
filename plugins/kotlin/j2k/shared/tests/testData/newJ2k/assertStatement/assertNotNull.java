abstract class C {
    String mMemberVariable;

    void foo() {
        String s1 = f();
        assert s1 != null;

        String s2 = g();
        assert s2 != null : "g should not return null";
        String doNotMergeDueToPossibleSideEffects = g();
        assert s2.hashCode() == 42;
        int h = s2.hashCode();

        String s3 = "sss";
        assert s3.hashCode() != null;

        assert doNotMergeDueToPossibleSideEffects != null;
        assert mMemberVariable != null;

        String doNotTouchDifferentAssert = f();
        assert doNotTouchDifferentAssert == null;
    }

    abstract String f();
    abstract String g();
}