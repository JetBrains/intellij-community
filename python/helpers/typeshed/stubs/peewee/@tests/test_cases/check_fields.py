from __future__ import annotations

from typing_extensions import assert_type

from peewee import BigBitField, BigBitFieldData, CharField, ForeignKeyField, IntegerField, Model


class User(Model):
    username = CharField()
    age = IntegerField()
    nickname = CharField(null=True)


class Tweet(Model):
    user = ForeignKeyField(User)
    author = ForeignKeyField(User, null=True)


class Event(Model):
    flags = BigBitField()


# A field is a descriptor that resolves differently depending on whether it is
# accessed on the model class or on an instance. `Model.field` is the Field
# object itself (used to build queries), while `instance.field` is the stored
# Python value.
assert_type(User.username, CharField[str])
assert_type(User().username, str)

assert_type(User.age, IntegerField[int])
assert_type(User().age, int)

# `null=True` allows the value to include None, both in the field's own
# parameterization and in the value produced on attribute access.
assert_type(User.nickname, CharField[str | None])
assert_type(User().nickname, str | None)

# Foreign keys resolve to the related model instance, or None when nullable.
assert_type(Tweet.user, ForeignKeyField[User])
assert_type(Tweet().user, User)
assert_type(Tweet().author, User | None)

# BigBitField is a special case: the instance descriptor yields a
# BigBitFieldData wrapper rather than the underlying bytes.
assert_type(Event.flags, BigBitField)
assert_type(Event().flags, BigBitFieldData)

# __set__ accepts the field's value type...
user = User()
user.username = "guido"
user.age = 42
user.nickname = None  # nullable field accepts None

# ...and rejects incompatible values.
user.age = "not an int"  # type: ignore
user.username = None  # type: ignore  # non-null field rejects None
