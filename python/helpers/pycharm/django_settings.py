import os

settings_file = os.getenv('PYCHARM_DJANGO_SETTINGS_MODULE') or os.getenv('DJANGO_SETTINGS_MODULE')
if not settings_file:
    settings_file = 'settings'

print ("Importing Django settings module " + settings_file)

try:
  settings_module = __import__(settings_file)

  components = settings_file.split('.')
  for comp in components[1:]:
      settings_module = getattr(settings_module, comp)

  for setting in dir(settings_module):
      if setting == setting.upper():
          globals()[setting] = getattr(settings_module, setting)
except ImportError:
  print ("There is no such settings file " + str(settings_file))

if globals().has_key("TEST_RUNNER"):
    globals()['USER_TEST_RUNNER'] = globals()['TEST_RUNNER']

TEST_RUNNER = 'pycharm.django_test_runner.run_tests'

