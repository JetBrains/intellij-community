package pack2;

import static pack1.A.staticMethod;
import pack1.A;

class Usage {
    {
        A.renamedStaticMethod(27);
    }
}