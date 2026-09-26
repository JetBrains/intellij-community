from typing import NoReturn

def stop() -> "NoReturn":
    raise RuntimeError('no way')

stop()
<warning descr="This code is unreachable">print("Should be reported as unreachable")</warning>