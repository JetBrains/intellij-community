class Settings(patterns.Observer, UnicodeAwareConfigParser):
   def __init__(self):
      pass
   def set(self, section, option, value, new=False):
        <selection>if new:
            currentValue = 'a new option, so use something as current value'\
                ' that is unlikely to be equal to the new value'</selection>
        else:
            currentValue = self.get(section, option)
        if value != currentValue:
            patterns.Event('before.%s.%s'%(section, option), self, value).send()
            super(Settings, self).set(section, option, value)
            patterns.Event('%s.%s'%(section, option), self, value).send()
