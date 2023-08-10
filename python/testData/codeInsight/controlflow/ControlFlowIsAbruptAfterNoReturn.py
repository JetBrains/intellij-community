 from typing import NoReturn

 def stop() -> NoReturn:
    raise RuntimeError('no way')

stop()
print("ureachable")