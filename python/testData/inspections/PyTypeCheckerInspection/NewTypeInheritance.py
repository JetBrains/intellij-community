from typing import NewType

UserId = NewType("UserId", int)
NewId = NewType("NewId", UserId)
ChildNewId = NewType("ChildNewId", NewId)

def get_user_super(user: UserId) -> str:
    pass

def get_user_child(user: NewId) -> str:
    pass

def get_user_child_new(user: ChildNewId):
    pass


user = UserId(12)
new_id = NewId(user)
child_new_id = ChildNewId(new_id)

get_user_super(user)
get_user_super(new_id)
get_user_super(child_new_id)

get_user_child(<warning descr="Expected type 'NewId', got 'UserId' instead">user</warning>)
get_user_child(new_id)
get_user_child(child_new_id)

get_user_child_new(<warning descr="Expected type 'ChildNewId', got 'UserId' instead">user</warning>)
get_user_child_new(<warning descr="Expected type 'ChildNewId', got 'NewId' instead">new_id</warning>)
get_user_child_new(child_new_id)