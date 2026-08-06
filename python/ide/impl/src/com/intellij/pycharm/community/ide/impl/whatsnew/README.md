# Want a new Vision WhatsNew page
Put the files under community\python\ide\impl\resources\whatsNew

Example files placement:
```
whatsNew
  - static.upl.6130.2026
  - pycharm2026.1.json
```

Vision main JSON should be named with pycharm{majorVersion}.{minorVersion}.json. For example: pycharm2026.1.json

# How showing of whatsNew works
We have ApplicationInfo.getInstance().majorVersion / minorVersion
For example, 2026.1, or 2027.3. 2026 - majorVersion, 1 - minorVersion.

If the file exists, we're disabling legacy WhatsNew
If missing, the legacy WhatsNew will be shown by its default rules.

# Where to look in code

We have two different WhatsNew pages which are working separately.

1. Original WhatsNew page via URL

`com.intellij.openapi.updateSettings.impl.JcefWhatsNewProjectActivity`
registered in
`community\platform\platform-impl\jcef\resources\intellij.platform.ide.impl.jcef.xml`
with `order="last"`

```<postStartupActivity implementation="com.intellij.openapi.updateSettings.impl.JcefWhatsNewProjectActivity" order="last"/>```


2. New WhatsNew Page via vision
   We have platform `com.intellij.platform.whatsNew.WhatsNewShowOnStartCheckService`
and PyCharm provider
   `com.intellij.pycharm.community.ide.impl.whatsnew.PyCharmWhatsNewInVisionContentProvider`
