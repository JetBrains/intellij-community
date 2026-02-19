from __future__ import annotations

from wtforms import DateField, Field, Form, StringField
from wtforms.validators import Email, Optional

form = Form()
# on form we should accept any validator mapping
form.validate({"field": (Optional(),), "string_field": (Optional(), Email())})
form.validate({"field": [Optional()], "string_field": [Optional(), Email()]})

# both StringField validators and Field validators should be valid
# as inputs on a StringField
string_field = StringField(validators=(Optional(), Email()))
string_field.validate(form, (Optional(), Email()))

# but not on Field
field = Field(validators=(Optional(), Email()))  # type: ignore
field.validate(form, (Optional(), Email()))  # type: ignore

# unless we only pass the Field validator
Field(validators=(Optional(),))
field.validate(form, (Optional(),))

# DateField should accept Field validators but not StringField validators
date_field = DateField(validators=(Optional(), Email()))  # type: ignore
date_field.validate(form, (Optional(), Email()))  # type: ignore
DateField(validators=(Optional(),))

# for lists we can't be as strict so we won't get type errors here
Field(validators=[Optional(), Email()])
field.validate(form, [Optional(), Email()])
DateField(validators=[Optional(), Email()])
date_field.validate(form, [Optional(), Email()])
