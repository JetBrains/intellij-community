
def foo3(x):
  '''
  >>> class User(Base):
  ...     __tablename__ = 'users'
  ...
  ...     <caret>id = Column(Integer, primary_key=True)
  ...     name = Column(String)
  ...       fullname = Column(String)
  ...
  ...     password = Column(String)
  '''
  pass

