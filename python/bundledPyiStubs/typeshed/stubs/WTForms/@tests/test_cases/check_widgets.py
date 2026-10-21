from __future__ import annotations

from wtforms import Field, FieldList, Form, FormField, SelectField, StringField
from wtforms.widgets import Input, ListWidget, Option, Select, TableWidget, TextArea

# more specific widgets should only work on more specific fields
Field(widget=Input())
Field(widget=TextArea())  # type: ignore
Field(widget=Select())  # type: ignore

# less specific widgets are fine, even if they're often not what you want
StringField(widget=Input())
StringField(widget=TextArea())

SelectField(widget=Input(), option_widget=Input())
SelectField(widget=Select(), option_widget=Option())
# a more specific type other than Option widget is not allowed
SelectField(widget=Select(), option_widget=TextArea())  # type: ignore

# we should be able to pass Field() even though it wants an unbound_field
# this gets around __new__ not working in type checking
FieldList(Field(), widget=Input())
FieldList(Field(), widget=ListWidget())

FormField(Form, widget=Input())
FormField(Form, widget=TableWidget())
