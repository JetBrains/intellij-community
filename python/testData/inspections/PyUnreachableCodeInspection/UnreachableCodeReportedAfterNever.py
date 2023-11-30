from typing import Never

def stop() -> Never:
    raise RuntimeError('no way')

stop()
<warning descr="This code is unreachable">print("Should be reported as unreachable")</warning>