class Client
{

    interface L { void m (); }

    class V implements L { 
	void m () { }
	void q () { }
    }

    //------------------------------------
 
    class C  {
	V method() { V res = new V ();  method().q(); return res; }
    }
    
    interface I { V method(); }

    class D extends C implements I { }
}
