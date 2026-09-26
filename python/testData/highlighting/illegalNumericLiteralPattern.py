match 42:
    case 0 + 1j:
        pass
    case <error descr="Invalid complex number literal">0 + 1</error>:
        pass
    case 0 - 1j:
        pass
    case <error descr="Invalid complex number literal">0 - 1</error>:
        pass
