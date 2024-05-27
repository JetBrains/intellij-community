package foo;

import bar.Older;

public class Younger extends Older {
    protected ProtectedObject v1  = ProtectedObject.INSTANCE;
    int v2 = ProtectedObject.INSTANCE.getInProtectedObject();
    protected ProtectedClass v3 = new ProtectedClass();
    int v4 = new ProtectedClass().getInProtectedClass();
    int v5 = protectedFun();
    int v6 = getProtectedVar();
}