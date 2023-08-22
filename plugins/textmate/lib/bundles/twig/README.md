<a href="https://marketplace.visualstudio.com/items?itemName=mblode.twig-language-2">
  <img src="https://github.com/mblode/vscode-twig-language-2/blob/master/images/icon.png?raw=true" alt="" width=100 height=100>
</a>

<h1>VS Code Twig Language 2 👋</h1>

<p>
  <img src="https://img.shields.io/badge/version-0.8.8-blue.svg?cacheSeconds=2592000" />
  <a href="https://github.com/mblode/vscode-twig-language-2/graphs/commit-activity">
    <img alt="Maintenance" src="https://img.shields.io/badge/Maintained%3F-yes-green.svg" target="_blank" />
  </a>
  <a href="https://github.com/mblode/vscode-twig-language-2/blob/master/LICENSE.md">
    <img alt="License: MIT" src="https://img.shields.io/badge/License-MIT-yellow.svg" target="_blank" />
  </a>
</p>

- Syntax highlighting
- Snippets
- Emmet
- Pretty Diff 3 Formatting
- Hover

## What has changed since version 1?

This extension does **not** have HTML Intellisense. If you need HTML Intellisense (which can be quite useful), please download my other Twig Language extension: https://github.com/mblode/vscode-twig-language.

I have created a new extension to fix the issues that I (and all of you) were having with file association, commenting and VS Code UI issues.

Simply add these lines to your VS Code settings to get emmet working and also to associate HTML files as twig syntax.

```
"files.associations": {
    "*.html": "twig"
},
"emmet.includeLanguages": {
    "twig": "html"
},
```

## Installation

Install through Visual Studio Code extensions. Search for `Twig Language 2`

[Visual Studio Code Market Place: Twig Language 2](https://marketplace.visualstudio.com/items?itemName=mblode.twig-language-2)

## Documentation

Twig Language 2 is a Visual Studio Code extension that provides snippets, syntax highlighting, hover, and formatting for the Twig file format.

### Twig syntax highlighting and language support

This extension provides language support for the Twig syntax.

### Code formatter/beautifier for Twig files

Using PrettyDiff, this extension implements the only working code formatter for Twig files in VS Code.

### Information about Twig code on hover

VS Code Twig language 2 shows information about the symbol/object that's below the mouse cursor when you hover within Twig files.

### Craft CMS/Twig code snippets

Adds a set of Craft CMS/Twig code snippets to use in your Twig templates.

### Generic Triggers

```twig

do                {% do ... %}
extends           {% extends 'template' %}
from              {% from 'template' import 'macro' %}
import            {% import 'template' as name %}
importself        {% import _self as name %}
inc, include      {% include 'template' %}
incp              {% include 'template' with params %}
inckv             {% include 'template' with { key: value } %}
use               {% use 'template' %}

autoescape        {% autoescape 'type' %}...{% endautoescape %}
block, blockb     {% block name %} ... {% endblock %}
blockf            {{ block('...') }}
embed             {% embed "template" %}...{% endembed %}
filter, filterb   {% filter name %} ... {% endfilter %}
macro             {% macro name(params) %}...{% endmacro %}
set, setb         {% set var = value %}
spaceless         {% spaceless %}...{% endspaceless %}
verbatim          {% verbatim %}...{% endverbatim %}

if, ifb           {% if condition %} ... {% endif %}
ife               {% if condition %} ... {% else %} ... {% endif %}
for               {% for item in seq %} ... {% endfor %}
fore              {% for item in seq %} ... {% else %} ... {% endfor %}

else              {% else %}
endif             {% endif %}
endfor            {% endfor %}
endset            {% endset %}
endblock          {% endblock %}
endfilter         {% endfilter %}
endautoescape     {% endautoescape %}
endembed          {% endembed %}
endfilter         {% endfilter %}
endmacro          {% endmacro %}
endspaceless      {% endspaceless %}
endverbatim       {% endverbatim %}

```

### Craft Triggers

```twig
asset                    craft.assets.one()
assets, assetso          craft.assets loop
categories, categorieso  craft.categories loop
entries, entrieso        craft.entries loop
feed                     craft.app.feeds.getFeedItems loop
t                        | t
replace                  | replace('search', 'replace')
replacex                 | replace('/(search)/i', 'replace')
split                    | split('\n')
tags, tagso              craft.tags loop
users, userso            craft.users loop

cache                    {% cache %}...{% endcache %}
children                 {% children %}
exit                     {% exit 404 %}
ifchildren               {% ifchildren %}...{% endifchildren %}
css                      {% css %}...{% endcss %}
registercssfile          {% do view.registerCssFile("/resources/css/global.css") %}
js                       {% js %}...{% endjs %}
registerjsfile           {% do view.registerJsFile("/resources/js/global.js") %}
matrix, matrixif         Basic Matrix field loop using if statements
matrixifelse             Basic Matrix field loop using if/elseif
matrixswitch             Basic Matrix field loop using switch
nav                      {% nav item in items %}...{% endnav %}
paginate                 Outputs example of pagination and prev/next links
redirect                 {% redirect 'login' %}
requirelogin             {% requireLogin %}
requirepermission        {% requirePermission "spendTheNight" %}
switch                   {% switch variable %}...{% endswitch %}

csrf                     {{ csrfInput() }}
endbody                  {{ endBody() }}
head                     {{ head() }}

getparam                 craft.app.request.getParam()
getbodyparam             craft.app.request.getBodyParam()
getqueryparam            craft.app.request.getQueryParam()
getsegment               craft.app.request.getSegment()

case                     {% case "value" %}
endcache                 {% endcache %}
endifchildren            {% endifchildren %}
endcss                   {% endcss %}
endjs                    {% endjs %}
endnav                   {% endnav %}

ceil                     ceil()
floor                    floor()
max                      max()
min                      min()
shuffle                  shuffle()
random                   random()
round                    num | round()
url, urla                url('path'), url('path', params, 'http', false)

rss                      Example rss feed

dd                       <pre>{{ dump() }}</pre>{% exit %}
dump                     <pre>{{ dump() }}</pre>
```

### Example Forms

```twig

formlogin                Example login form
formuserprofile          Example user profile form
formuserregistration     Example user registration form
formforgotpassword       Example forgot password form
formsetpassword          Example set password form
formsearch               Example search form
formsearchresults        Example search form results

```

### Reference Hints

```twig

info            All craft.assets properties and template tags
info            All craft.crategories properties and template tags
info            All craft.config properties and template tags
info            All craft.entries properties and template tags
info            All craft.feeds properties and template tags
info            All craft.fields properties and template tags
info            All craft.globals properties and template tags
info            All craft.request properties and template tags
info            All craft.sections properties and template tags
info            All craft.session properties and template tags
info            All craft.tags properties and template tags
info            All craft.users properties and template tags
info            All craft globals (site info, date, users, template tags)

```

## Author

👤 **Matthew Blode**

* Github: [@mblode](https://github.com/mblode)

## 🤝 Contributing

Contributions, issues and feature requests are welcome !<br />Feel free to check [issues page](https://github.com/mblode/vscode-twig-language-2/issues).

## Show your support

Give a ⭐️ if this project helped you !

## 📝 License

Copyright © 2019 [Matthew Blode](https://github.com/mblode).<br />
This project is [MIT](https://github.com/mblode/vscode-twig-language-2/blob/master/LICENSE.md) licensed.
