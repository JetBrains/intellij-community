class Client
{

    interface L { void m (); }

    class V implements L { 
	void m () { }
	void q () { }
    }

    //------------------------------------
 
    class C  {
	int method(L v) {  return 0; }
    }
    
    interface I { int method(L v); }

    class D extends C implements I { }
}
