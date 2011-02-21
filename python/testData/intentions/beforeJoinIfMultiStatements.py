class A:
  def __init__(self, *args, **kwargs):
    self.uses_remaining_this_turn = None
    self.effects = None

  def foo(self):
    if self.can_act<caret>ivate():
      if self.effect_queue is None:
        self.effect_queue = list(self.effects)
      for effect in self.effect_queue:
        effect.activate(source, targets)
      self.effect_queue = None
      self.uses_remaining_this_turn -= 1

  def can_activate(self):
    pass