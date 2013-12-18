IntelliJ IDEA Postfix Completion plugin
---------------------------------------

The basic idea is to prevent caret jumps backward while typing code,
let you start with the some expression, explore some APIs, think about
what you are going to do and after finish with statement of some type.

#### Examples
![options](/content/example.png)

See [more examples](examples.md).

#### Download

Plugin currently is under development.
Pre-release versions are available for download in [IntelliJ IDEA plugin repository](http://plugins.jetbrains.com/plugin/7342).
To install into IDEA simply go *Preferences* – *Plugins* – *Browse repositories...* and search for *"postfix"*.

#### Version

Currently plugin tested and works well with IDEA 13 CE (since first preview build v130.754),
but you can easily build it against IDEA 12 SDK. When main development phase will be finished,
I'll investigate ability to work in IDEA 12 and lower the required version if possible.

#### Features

Available templates:

* `.if` – checks boolean expression to be true `if (expr)`
* `.else` – checks boolean expression to be false `if (!expr)`
* `.var` – initialize new variable with expression `T name = expr;`
* `.null` – checks nullable expression to be null `if (expr == null)`
* `.notnull` – checks expression to be non-null `if (expr != null)`
* `.instanceof` – checks type `expr instanceof T ? ((T) expr).m : null`
* `.not` – negates value of inner boolean expression `!expr`
* `.for` – iterates over collection `for (T item : collection)`
* `.while` – uses expression as loop condition `while (expr)`
* `.arg` – helps surround argument with invocation `method(expr)`
* `.cast` – surrounds expression with cast `(SomeType) expr`
* `.new` – produces instantiation expression for type `new T()`
* `.fori` – surrounds with loop `for (int i = 0; i < expr.length; i++)`
* `.forr` – reverse loop `for (int i = expr.length - 1; i >= 0; i--)`
* `.field` – introduces field for expression `_field = expr;`
* `.par` – surrounds outer expression with parentheses `(expr)`
* `.return` – returns value from containing method `return expr;`
* `.switch` – switch over integral/enum/string values `switch (expr)`
* `.throw` – throws exception of 'Throwable' type `throw new Exception();`
* `.assert` - creates assertion from boolean expression `assert expr;`
* `.synchronized` – produces synchronized block `synchronized (expr)`

Other features:

* Template expansion by `Tab` key in editor (like live templates)
* Settings page to disable/enable particular postfix templates

Future work:
* `.try` – surrounds resource expression with `try (T resource = expr)`
* Control braces insertion for statements (use code style settings?)
* Completion char handling `expr.var.usage()` => `T x = expr; x.usage()`?
* `1..10` - for (int i = 1; i < 10; i++) {}
* `list.each` - for (E e : list) {}
* `.for` should be equals to fori for int expressions and to `.each` for iterable expressions
* `.while` should expand iterator expressions to while(iterator.hasNext()) { SomeType next = iterator.next() } 
* `map`, `filter` and other stuff from `ContainerUtil` or Guava collections
* Support non-Java languages

#### Feedback

Feel free to post any issues or feature requests here on github or contact me directly:
* *alexander.shvedov[at]jetbrains.com*
