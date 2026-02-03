from typing import Never

def stop() -> Never:
    raise RuntimeError('no way')

stop()
print("ureachable")