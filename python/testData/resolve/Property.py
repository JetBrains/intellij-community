class Person(object):
   def get_full_name(self):
       return "%s %s" % (self.first_name, self.last_name)

   def set_full_name(self, full_name):
       self.first_name, self.last_name = full_name.split()

   full_name = property(get_full_name, set_full_name)

p = Person()
p.fu<ref>ll_name = "Dmitry Jemerov"
