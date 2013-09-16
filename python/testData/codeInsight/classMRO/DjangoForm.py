class BaseForm(object): pass
class DeclarativeFieldsMetaclass(type): pass

class Form(six.with_metaclass(DeclarativeFieldsMetaclass, BaseForm)):
    pass