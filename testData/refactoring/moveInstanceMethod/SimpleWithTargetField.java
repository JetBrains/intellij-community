class Foreign {
}

public abstract class Test1 {
    int field;
    Foreign myForeign;

    void <caret>foo () {
        field++;
    }

    void bar () {
        foo();
    }
}
