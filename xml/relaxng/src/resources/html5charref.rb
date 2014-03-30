#! /usr/bin/env ruby
CHAR_REFS_URL = 'http://dev.w3.org/html5/html-author/charref'
refs = `curl #{CHAR_REFS_URL}`

header = <<header
<!--
 * Copyright 2000-#{Time.now.year} JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 -->

<!-- It's an automatically generated code. Do not modify it. -->
<!-- Please see #{CHAR_REFS_URL} and #{$0} -->

header

body = ""
refs.scan(/<td class="named"><code>&amp;((\w|; &amp;)+);\<\/code><td class="hex"><code>&amp;(#([^;])*);<\/code><td class="dec"><code>&amp;(#([^;])*);<\/code>/) do | match |
  $1.split("; &amp;").each do | name |
    body = body + " <!ENTITY #{name}  \"&#{$5};\" >\n"
  end
end


footer = <<footer
footer

puts header + body + footer