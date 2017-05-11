/* -*- Mode: Javascript; indent-tabs-mode:nil; js-indent-level: 2 -*- */
/* vim: set ts=2 et sw=2 tw=80: */

/*************************************************************
 *
 *  MathJax/localization/zh-hans/HelpDialog.js
 *
 *  Copyright (c) 2009-2015 The MathJax Consortium
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

MathJax.Localization.addTranslation("zh-hans","HelpDialog",{
        version: "2.6.0",
        isLoaded: true,
        strings: {
          Help: "MathJax\u5E2E\u52A9",
          MathJax: "*MathJax*\u662F\u4E00\u79CD\u8BA9\u7F51\u9875\u5236\u4F5C\u8005\u5F80\u7F51\u9875\u4E2D\u52A0\u5165\u6570\u5B66\u5F0F\u7684JavaScript\u8FD0\u884C\u5E93\u3002\u4F5C\u4E3A\u8BFB\u8005\uFF0C\u60A8\u4E0D\u9700\u8981\u505A\u4EFB\u4F55\u4F7F\u5176\u51FA\u73B0\u7684\u4E8B\u60C5\u3002",
          Browsers: "*\u6D4F\u89C8\u5668*\uFF1AMathJax\u53EF\u4EE5\u5728\u6240\u6709\u8F83\u65B0\u6D4F\u89C8\u5668\u4E0A\u5DE5\u4F5C\uFF0C\u5305\u62ECIE6+\u3001Firefox 3+\u3001Chrome 0.2+\u3001Safari 2+\u3001Opera 9.6+\u548C\u5927\u591A\u6570\u79FB\u52A8\u6D4F\u89C8\u5668\u3002",
          Menu: "*\u6570\u5F0F\u83DC\u5355*\uFF1AMathJax\u7ED9\u6570\u5F0F\u6DFB\u52A0\u4E86\u5FEB\u6377\u83DC\u5355\u3002\u53F3\u952E\u6216\u6309Ctrl\u7136\u540E\u70B9\u51FB\u4EFB\u4F55\u6570\u5F0F\u5373\u53EF\u8FDB\u5165\u83DC\u5355\u3002",
          ShowMath: "*\u6570\u5F0F\u663E\u793A\u5F62\u5F0F*\u5141\u8BB8\u60A8\u6D4F\u89C8\u516C\u5F0F\u7684\u6E90\u4EE3\u7801\u4EE5\u4FBF\u590D\u5236\u7C98\u8D34\uFF08\u4EE5\u539F\u59CB\u5F62\u5F0F\u6216MathML\uFF09\u3002",
          Settings: "*\u6570\u5B66\u8BBE\u7F6E*\u91CC\u60A8\u60A8\u53EF\u4EE5\u8C03\u6574MathJax\u7684\u5404\u79CD\u529F\u80FD\uFF0C\u6BD4\u5982\u8BF4\u6570\u5F0F\u7684\u5927\u5C0F\uFF0C\u548C\u663E\u793A\u7B49\u5F0F\u7684\u673A\u7406\u3002",
          Language: "*\u8BED\u8A00*\u7ED9\u60A8\u9009\u62E9MathJax\u7528\u4F5C\u83DC\u5355\u548C\u8B66\u544A\u4FE1\u606F\u7684\u8BED\u8A00\u3002",
          Zoom: "*\u6570\u5F0F\u7F29\u653E*\uFF1A\u5982\u679C\u60A8\u6D4F\u89C8\u7B49\u5F0F\u65F6\u9047\u5230\u56F0\u96BE\uFF0CMathJax\u53EF\u4EE5\u5C06\u5176\u653E\u5927\u6765\u4F7F\u60A8\u83B7\u5F97\u66F4\u597D\u7684\u4F53\u9A8C\u3002",
          Accessibilty: "*\u8F85\u52A9\u529F\u80FD*\uFF1AMathJax\u4F1A\u81EA\u52A8\u4E0E\u8BBF\u95EE\u8005\u4EA4\u4E92\u4F7F\u89C6\u89C9\u969C\u788D\u8005\u7406\u89E3\u6570\u5F0F\u66F4\u52A0\u5BB9\u6613\u3002",
          Fonts: "*\u5B57\u4F53*\uFF1AMathJax\u5C06\u4F1A\u4F7F\u7528\u60A8\u7535\u8111\u4E0A\u5B89\u88C5\u7684\u67D0\u4E9B\u6570\u5F0F\u5B57\u4F53\u6765\u663E\u793A\u6570\u5F0F\uFF1B\u5982\u679C\u6CA1\u6709\u5B89\u88C5\u7684\u8BDD\uFF0C\u5B83\u5C06\u4F7F\u7528\u7F51\u7EDC\u4E0A\u7684\u5B57\u4F53\u3002\u867D\u7136\u5E76\u975E\u5FC5\u8981\uFF0C\u4F46\u662F\u5C06\u8FD9\u4E9B\u5B57\u4F53\u5B89\u88C5\u5230\u672C\u5730\u80FD\u52A0\u901F\u6570\u5F0F\u7684\u663E\u793A\u3002\u6211\u4EEC\u5EFA\u8BAE\u60A8\u5B89\u88C5[STIX fonts](%1)\u3002"
        }
});

MathJax.Ajax.loadComplete("[MathJax]/localization/zh-hans/HelpDialog.js");
