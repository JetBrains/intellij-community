# Class could be designed to be used in cooperative multiple inheritance
# so `super()` could be resolved to some non-object class that is able to receive passed arguments.


class Shape(object):
  def __init__(self, shapename, **kwds):
      self.shapename = shapename
      # in case of ColoredShape the call below will be executed on Colored
      # so warning should not be raised
      super(Shape, self).__init__(**kwds)


class Colored(object):
    def __init__(self, color, **kwds):
        self.color = color
        super(Colored, self).__init__(**kwds)


class ColoredShape(Shape, Colored):
    pass


cs = ColoredShape(color='red', shapename='circle')
