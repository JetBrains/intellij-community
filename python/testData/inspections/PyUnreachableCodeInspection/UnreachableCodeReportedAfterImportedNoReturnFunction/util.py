from typing import NoReturn

def panic(m) -> NoReturn:
    print(f'Help: {m}')
    raise SystemExit