variable1 = 'variable'
variable = f'test{variable1}42' + "42" + f"{variable1}"

target = f'{varia<caret>ble + "42" + f"42{variable1}"}/a'
