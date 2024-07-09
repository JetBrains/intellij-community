from typing import NoReturn

class Alarmist:
    def panic(m) -> NoReturn:
        print(f'Help: {m}')
        raise SystemExit