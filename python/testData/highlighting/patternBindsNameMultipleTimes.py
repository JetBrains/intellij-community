match 42:
    case x, <error descr="Name 'x' is already bound">x</error>:
        pass
    case x, (1 as <error descr="Name 'x' is already bound">x</error>) | (2 as <error descr="Name 'x' is already bound">x</error>):
        pass    
    case [(1 as x) | (2 as x), <error descr="Name 'x' is already bound">x</error>]:
        pass
    case [(1 as x) | (2 as x), (1 as <error descr="Name 'x' is already bound">x</error>) | (2 as <error descr="Name 'x' is already bound">x</error>)]:
        pass
    case ((1 as x) | (2 as x)) | (3 as x):
        pass
    case x, ((1 as <error descr="Name 'x' is already bound">x</error>) | (2 as <error descr="Name 'x' is already bound">x</error>)) | (3 as <error descr="Name 'x' is already bound">x</error>):
        pass
    case (x, (1 as <error descr="Name 'x' is already bound">x</error>) | (2 as <error descr="Name 'x' is already bound">x</error>)) | (3 as x):
        pass
