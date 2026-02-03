class ToClass(object):
    CLASS_FIELD = 42

    def __init__(self):
        self.instance_field = 100500


class FromClass(ToClass):
  def __init__(self):
      pass
