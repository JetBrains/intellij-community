from typing import NamedTuple


class User(NamedTuple):
    class PrivLvl:
        ADMIN = 1
        OPERATOR = 2

    name: str
    level: PrivLvl = PrivLvl.ADMIN

User.PrivLvl

admin = User('MrRobot')
admin.PrivLvl