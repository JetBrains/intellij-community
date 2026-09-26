from __future__ import annotations

from typing_extensions import assert_type

from wtforms import Form, StringField
from wtforms.fields.core import UnboundField


class MyForm(Form):
    name = StringField()


form = MyForm()
assert_type(form, MyForm)
assert_type(form.name, StringField)
assert_type(MyForm.name, UnboundField[StringField])
