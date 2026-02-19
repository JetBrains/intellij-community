match 42:
    case <error descr="Pattern does not bind name y">[x]</error> | <error descr="Pattern does not bind name x">[y]</error>: 
        pass
    case <error descr="Pattern does not bind name z">[x, y]</error> | <error descr="Pattern does not bind name x">[y, z]</error>:
        pass
    case <error descr="Pattern does not bind name z">(<error descr="Pattern does not bind name y">[x]</error> | <error descr="Pattern does not bind name x">[y]</error>)</error> | <error descr="Pattern does not bind names x, y">[z]</error>:
        pass
