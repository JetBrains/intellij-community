class Test
{
    public Object foo() {
        Object result = null;
        <selection>if(test1()) return null;
        if(test2()) return null;</selection>
        return result;
    }
    public int foo1() {
        int result = 1;
        if(test1()) return 0;
        if(test2()) return 0;
        return result;
    }

    private boolean test1() {return false;}
    private boolean test2() {return false;}
}