// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.schema;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.jsonSchema.JsonSchemaHighlightingTestBase;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.YAMLLanguage;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class YamlByYamlSchemaHighlightingTest extends JsonSchemaHighlightingTestBase {
  @NotNull
  @Override
  public String getTestDataPath() {
    return PathManagerEx.getCommunityHomePath() + "/plugins/yaml/testSrc/org/jetbrains/yaml/schema/data/highlighting";
  }

  @Override
  protected String getTestFileName() {
    return "config.yml";
  }

  @Override
  protected InspectionProfileEntry getInspectionProfile() {
    return new YamlJsonSchemaHighlightingInspection();
  }


  @Override
  protected Predicate<VirtualFile> getAvailabilityPredicate() {
    return file -> file.getFileType() instanceof LanguageFileType && ((LanguageFileType)file.getFileType()).getLanguage().isKindOf(
      YAMLLanguage.INSTANCE);
  }

  private void doTestYaml(@Language("YAML") @NotNull final String schema, @NotNull final String text) {
    configureInitially(schema, text, "yaml");
    myFixture.checkHighlighting(true, false, false);
  }

  public void testEnum1() {
    @Language("YAML") final String schema = "properties:\n" +
                                            "  prop: \n" +
                                            "    \"enum\":\n" +
                                            "      - 1\n" +
                                            "      - 2\n" +
                                            "      - 3\n" +
                                            "      - \"18\"";
    doTestYaml(schema, "prop: 1");
    doTestYaml(schema, "prop: <warning>foo</warning>");
  }

  public void testMissingProp() {
    @Language("YAML") final String schema = "properties: \n" +
                                            "  prop: {}\n" +
                                            "  flop: {}\n" +
                                            "required:\n" +
                                            "  - flop";

    doTestYaml(schema, "<warning>prop: 2</warning>");
    doTestYaml(schema, "prop: 2\nflop: a");
    doTestYaml(schema, "flop: a");
  }

  public void testNumberMultiple() {
    @Language("YAML") String schema = "properties:\n" +
                                      "  prop:\n" +
                                      "    type: number\n" +
                                      "    multipleOf: 2";
    doTestYaml(schema, "prop: <warning descr=\"Schema validation: Is not multiple of 2\">3</warning>");
    doTestYaml(schema, "prop: 4");
  }

  public void testNumberMinMax() {
    doTestYaml("properties:\n" +
               "  prop: \n" +
               "    type: number\n" +
               "    minimum: 0\n" +
               "    maximum: 100\n" +
               "    exclusiveMaximum: true", "prop: 14");
  }

  public void testEnum() {
    @Language("YAML") final String schema = "properties:\n" +
                                            "  prop:\n" +
                                            "    enum:\n" +
                                            "      - 1\n" +
                                            "      - 2\n" +
                                            "      - 3\n" +
                                            "      - \"18\"";
    doTestYaml(schema, "prop: 18");
    doTestYaml(schema, "prop: 2");
    doTestYaml(schema, "prop: <warning descr=\"Schema validation: Value should be one of: 1, 2, 3, \\\"18\\\"\">6</warning>");
  }

  public void testSimpleString() {
    @Language("YAML") final String schema = "properties:\n" +
                                            "  prop: \n" +
                                            "    type: string\n" +
                                            "    minLength: 2\n" +
                                            "    maxLength: 3";
    doTestYaml(schema, "prop: <warning descr=\"Schema validation: String is shorter than 2\">s</warning>");
    doTestYaml(schema, "prop: sh");
    doTestYaml(schema, "prop: sho");
    doTestYaml(schema, "prop: <warning descr=\"Schema validation: String is longer than 3\">shor</warning>");
  }

  public void testArray() {
    @Language("YAML") final String schema = schema("{\n" +
                                                   "  \"type\": \"array\",\n" +
                                                   "  \"items\": {\n" +
                                                   "    \"type\": \"number\", \"minimum\": 18" +
                                                   "  }\n" +
                                                   "}");
    doTestYaml(schema, "prop:\n - 101\n - 102");
    doTestYaml(schema, "prop:\n - <warning descr=\"Schema validation: Less than the minimum 18\">16</warning>");
    doTestYaml(schema, "prop:\n - <warning descr=\"Schema validation: Incompatible types.\n Required: number. Actual: string.\">test</warning>");
  }

  public void testTopLevelArray() {
    @Language("YAML") final String schema = "type: array\n" +
                                            "items:\n" +
                                            "  type: number\n" +
                                            "  minimum: 18\n";
    doTestYaml(schema, "- 101\n- 102");
  }

  public void testTopLevelObjectArray() {
    @Language("YAML") final String schema = "{\n" +
                                            "  \"type\": \"array\",\n" +
                                            "  \"items\": {\n" +
                                            "    \"type\": \"object\", \"properties\": {\"a\": {\"type\": \"number\"}}" +
                                            "  }\n" +
                                            "}";
    doTestYaml(schema, "- a: <warning descr=\"Schema validation: Incompatible types.\n Required: number. Actual: boolean.\">true</warning>");
    doTestYaml(schema, "- a: 18");
  }

  public void testArrayTuples1() {
    @Language("YAML") final String schema = schema("{\n" +
                                                   "  \"type\": \"array\",\n" +
                                                   "  \"items\": [{\n" +
                                                   "    \"type\": \"number\", \"minimum\": 18" +
                                                   "  }, {\"type\" : \"string\"}]\n" +
                                                   "}");
    doTestYaml(schema,
               "prop:\n - 101\n - <warning descr=\"Schema validation: Incompatible types.\n Required: string. Actual: integer.\">102</warning>");
  }

  public void testArrayTuples2() {
    @Language("YAML") final String schema2 = schema("{\n" +
                                                    "  \"type\": \"array\",\n" +
                                                    "  \"items\": [{\n" +
                                                    "    \"type\": \"number\", \"minimum\": 18" +
                                                    "  }, {\"type\" : \"string\"}],\n" +
                                                    "\"additionalItems\": false}");
    doTestYaml(schema2, "prop:\n - 101\n - <warning descr=\"Schema validation: Incompatible types.\n Required: string. Actual: integer.\">102</warning>\n - <warning descr=\"Schema validation: Additional items are not allowed\">additional</warning>");
  }

  public void testArrayLength() {
    @Language("YAML") final String schema = schema("{\"type\": \"array\", \"minItems\": 2, \"maxItems\": 3}");
    doTestYaml(schema, "prop:\n <warning descr=\"Schema validation: Array is shorter than 2\">- 1</warning>");
    doTestYaml(schema, "prop:\n - 1\n - 2");
    doTestYaml(schema, "prop:\n <warning descr=\"Schema validation: Array is longer than 3\">- 1\n - 2\n - 3\n - 4</warning>");
  }

  public void testArrayUnique() {
    @Language("YAML") final String schema = schema("{\"type\": \"array\", \"uniqueItems\": true}");
    doTestYaml(schema, "prop:\n - 1\n - 2");
    doTestYaml(schema, "prop:\n - <warning descr=\"Schema validation: Item is not unique\">1</warning>\n - 2\n - test\n - <warning descr=\"Schema validation: Item is not unique\">1</warning>");
  }

  public void testMetadataIsOk() {
    @Language("YAML") final String schema = "{\n" +
                                            "  \"title\" : \"Match anything\",\n" +
                                            "  \"description\" : \"This is a schema that matches anything.\",\n" +
                                            "  \"default\" : \"Default value\"\n" +
                                            "}";
    doTestYaml(schema, "anything: 1");
  }

  public void testRequiredField() {
    @Language("YAML") final String schema = "{\"type\": \"object\", \"properties\": {\"a\": {}, \"b\": {}}, \"required\": [\"a\"]}";
    doTestYaml(schema, "a: 11");
    doTestYaml(schema, "a: 1\nb: true");
    doTestYaml(schema, "<warning descr=\"Schema validation: Missing required property 'a'\">b: alarm</warning>");
  }

  public void testInnerRequired() {
    @Language("YAML") final String schema = schema("{\"type\": \"object\", \"properties\": {\"a\": {}, \"b\": {}}, \"required\": [\"a\"]}");
    doTestYaml(schema, "prop:\n a: 11");
    doTestYaml(schema, "prop:\n a: 1\n b: true");
    doTestYaml(schema, "prop:\n <warning descr=\"Schema validation: Missing required property 'a'\">b: alarm</warning>");
  }

  public void testAdditionalPropertiesAllowed() {
    @Language("YAML") final String schema = schema("{}");
    doTestYaml(schema, "prop:\n q: true\n someStuff: 20");
  }

  public void testAdditionalPropertiesDisabled() {
    @Language("YAML") final String schema = "{\"type\": \"object\", \"properties\": {\"prop\": {}}, \"additionalProperties\": false}";
    // not sure abt inner object
    doTestYaml(schema, "prop:\n q: true\n<warning descr=\"Schema validation: Property 'someStuff' is not allowed\">someStuff: 20</warning>");
  }

  public void testAdditionalPropertiesSchema() {
    @Language("YAML") final String schema = "{\"type\": \"object\", \"properties\": {\"a\": {}}," +
                                            "\"additionalProperties\": {\"type\": \"number\"}}";
    doTestYaml(schema, "a: moo\nb: 5\nc: <warning descr=\"Schema validation: Incompatible types.\n Required: number. Actual: string.\">foo</warning>");
  }

  public void testMinMaxProperties() {
    @Language("YAML") final String schema = "{\"type\": \"object\", \"minProperties\": 2, \"maxProperties\": 3}";
    doTestYaml(schema, "<warning descr=\"Schema validation: Number of properties is less than 2\">a: 3</warning>");
    doTestYaml(schema, "a: 1\nb: 5");
    doTestYaml(schema, "<warning descr=\"Schema validation: Number of properties is greater than 3\">a: 1\nb: 22\nc: 333\nd: 4444</warning>");
  }

  public void testOneOf() {
    final List<String> subSchemas = new ArrayList<>();
    subSchemas.add("{\"type\": \"number\"}");
    subSchemas.add("{\"type\": \"boolean\"}");
    @Language("YAML") final String schema = schema("{\"oneOf\": [" + StringUtil.join(subSchemas, ", ") + "]}");
    doTestYaml(schema, "prop: 5");
    doTestYaml(schema, "prop: true");
    doTestYaml(schema, "prop: <warning descr=\"Schema validation: Incompatible types.\n Required one of: boolean, number. Actual: string.\">aaa</warning>");
  }

  public void testOneOfForTwoMatches() {
    final List<String> subSchemas = new ArrayList<>();
    subSchemas.add("{\"type\": \"string\", \"enum\": [\"a\", \"b\"]}");
    subSchemas.add("{\"type\": \"string\", \"enum\": [\"a\", \"c\"]}");
    @Language("YAML") final String schema = schema("{\"oneOf\": [" + StringUtil.join(subSchemas, ", ") + "]}");
    doTestYaml(schema, "prop: b");
    doTestYaml(schema, "prop: c");
    doTestYaml(schema, "prop: <warning descr=\"Schema validation: Validates to more than one variant\">a</warning>");
  }

  public void testOneOfSelectError() {
    final List<String> subSchemas = new ArrayList<>();
    subSchemas.add("{\"type\": \"string\",\n" +
                   "          \"enum\": [\n" +
                   "            \"off\", \"warn\", \"error\"\n" +
                   "          ]}");
    subSchemas.add("{\"type\": \"integer\"}");
    @Language("YAML") final String schema = schema("{\"oneOf\": [" + StringUtil.join(subSchemas, ", ") + "]}");
    doTestYaml(schema, "prop: off");
    doTestYaml(schema, "prop: 12");
    doTestYaml(schema, "prop: <warning descr=\"Schema validation: Value should be one of: \\\"off\\\", \\\"warn\\\", \\\"error\\\"\">wrong</warning>");
  }

  public void testAnyOf() {
    final List<String> subSchemas = new ArrayList<>();
    subSchemas.add("{\"type\": \"string\", \"enum\": [\"a\", \"b\"]}");
    subSchemas.add("{\"type\": \"string\", \"enum\": [\"a\", \"c\"]}");
    @Language("YAML") final String schema = schema("{\"anyOf\": [" + StringUtil.join(subSchemas, ", ") + "]}");
    doTestYaml(schema, "prop: b");
    doTestYaml(schema, "prop: c");
    doTestYaml(schema, "prop: a");
    doTestYaml(schema, "prop: <warning descr=\"Schema validation: Value should be one of: \\\"a\\\", \\\"b\\\", \\\"c\\\"\">d</warning>");
  }

  public void testAllOf() {
    final List<String> subSchemas = new ArrayList<>();
    subSchemas.add("{\"type\": \"integer\", \"multipleOf\": 2}");
    subSchemas.add("{\"enum\": [1,2,3]}");
    @Language("YAML") final String schema = schema("{\"allOf\": [" + StringUtil.join(subSchemas, ", ") + "]}");
    doTestYaml(schema, "prop: <warning descr=\"Schema validation: Is not multiple of 2\">1</warning>");
    doTestYaml(schema, "prop: <warning descr=\"Schema validation: Value should be one of: 1, 2, 3\">4</warning>");
    doTestYaml(schema, "prop: 2");
  }

  // ----

  public void testObjectInArray() {
    @Language("YAML") final String schema = schema("{\"type\": \"array\", \"items\": {\"type\": \"object\"," +
                                                   "\"properties\": {" +
                                                   "\"innerType\":{}, \"innerValue\":{}" +
                                                   "}, \"additionalProperties\": false" +
                                                   "}}");
    doTestYaml(schema, "prop:\n- innerType: aaa\n  <warning descr=\"Schema validation: Property 'alien' is not allowed\">alien: bee</warning>");
  }

  public void testObjectDeeperInArray() {
    final String innerTypeSchema = "{\"properties\": {\"only\": {}}, \"additionalProperties\": false}";
    @Language("YAML") final String schema = schema("{\"type\": \"array\", \"items\": {\"type\": \"object\"," +
                                                   "\"properties\": {" +
                                                   "\"innerType\":" + innerTypeSchema +
                                                   "}, \"additionalProperties\": false" +
                                                   "}}");
    doTestYaml(schema,
           "prop:\n- innerType:\n   only: true\n   <warning descr=\"Schema validation: Property 'hidden' is not allowed\">hidden: false</warning>");
  }

  public void testInnerObjectPropValueInArray() {
    @Language("YAML") final String schema = "{\"properties\": {\"prop\": {\"type\": \"array\", \"items\": {\"enum\": [1,2,3]}}}}";
    doTestYaml(schema, "prop:\n - 1\n - 3");
    doTestYaml(schema, "prop:\n - <warning descr=\"Schema validation: Value should be one of: 1, 2, 3\">out</warning>");
  }

  public void testAllOfProperties() {
    @Language("YAML") final String schema = "{\"allOf\": [{\"type\": \"object\", \"properties\": {\"first\": {}}}," +
                                            " {\"properties\": {\"second\": {\"enum\": [33,44]}}}], \"additionalProperties\": false}";
    //    doTestYaml(schema, "first: true\nsecond: <warning descr=\"Schema validation: Value should be one of: [33, 44]\">null</warning>");
    doTestYaml(schema, "first: true\nsecond: 44\n<warning descr=\"Schema validation: Property 'other' is not allowed\">other: 15</warning>");
    doTestYaml(schema, "first: true\nsecond: <warning descr=\"Schema validation: Value should be one of: 33, 44\">12</warning>");
  }

  public void testWithWaySelection() {
    final String subSchema1 = "{\"enum\": [1,2,3,4,5]}";
    final String subSchema2 = "{\"type\": \"array\", \"items\": {\"properties\": {\"kilo\": {}}, \"additionalProperties\": false}}";
    @Language("YAML") final String schema = "{\"properties\": {\"prop\": {\"oneOf\": [" + subSchema1 + ", " + subSchema2 + "]}}}";
    doTestYaml(schema, "prop:\n - <warning descr=\"Schema validation: Property 'foxtrot' is not allowed\">foxtrot: 15</warning>\n   kilo: 20");
  }

  public void testPatternPropertiesHighlighting() {
    @Language("YAML") final String schema = "{\n" +
                                            "  \"patternProperties\": {\n" +
                                            "    \"^A\" : {\n" +
                                            "      \"type\": \"number\"\n" +
                                            "    },\n" +
                                            "    \"B\": {\n" +
                                            "      \"type\": \"boolean\"\n" +
                                            "    },\n" +
                                            "    \"C\": {\n" +
                                            "      \"enum\": [\"test\", \"em\"]\n" +
                                            "    }\n" +
                                            "  }\n" +
                                            "}";
    doTestYaml(schema, "Abezjana: 2\n" +
                   "Auto: <warning descr=\"Schema validation: Incompatible types.\n Required: number. Actual: string.\">no</warning>\n" +
                   "BAe: <warning descr=\"Schema validation: Incompatible types.\n Required: boolean. Actual: integer.\">22</warning>\n" +
                   "Boloto: <warning descr=\"Schema validation: Incompatible types.\n Required: boolean. Actual: integer.\">2</warning>\n" +
                   "Cyan: <warning descr=\"Schema validation: Value should be one of: \\\"test\\\", \\\"em\\\"\">me</warning>\n");
  }

  public void testPatternPropertiesFromIssue() {
    @Language("YAML") final String schema = "{\n" +
                                            "  \"type\": \"object\",\n" +
                                            "  \"additionalProperties\": false,\n" +
                                            "  \"patternProperties\": {\n" +
                                            "    \"p[0-9]\": {\n" +
                                            "      \"type\": \"string\"\n" +
                                            "    },\n" +
                                            "    \"a[0-9]\": {\n" +
                                            "      \"enum\": [\"auto!\"]\n" +
                                            "    }\n" +
                                            "  }\n" +
                                            "}";
    doTestYaml(schema,
           "p1: <warning descr=\"Schema validation: Incompatible types.\n Required: string. Actual: integer.\">1</warning>\n" +
           "p2: <warning descr=\"Schema validation: Incompatible types.\n Required: string. Actual: integer.\">3</warning>\n" +
           "a2: auto!\n" +
           "a1: <warning descr=\"Schema validation: Value should be one of: \\\"auto!\\\"\">moto!</warning>\n"
    );
  }

  public void testPatternForPropertyValue() {
    @Language("YAML") final String schema = "{\n" +
                                            "  \"properties\": {\n" +
                                            "    \"withPattern\": {\n" +
                                            "      \"pattern\": \"p[0-9]\"\n" +
                                            "    }\n" +
                                            "  }\n" +
                                            "}";
    final String correctText = "withPattern: p1";
    final String wrongText = "withPattern: <warning descr=\"Schema validation: String violates the pattern: 'p[0-9]'\">wrong</warning>";
    doTestYaml(schema, correctText);
    doTestYaml(schema, wrongText);
  }

  public void testPatternWithSpecialEscapedSymbols() {
    @Language("YAML") final String schema = "{\n" +
                                            "  \"properties\": {\n" +
                                            "    \"withPattern\": {\n" +
                                            "      \"pattern\": \"^\\\\d{4}\\\\-(0?[1-9]|1[012])\\\\-(0?[1-9]|[12][0-9]|3[01])$\"\n" +
                                            "    }\n" +
                                            "  }\n" +
                                            "}";
    @Language("yaml") final String correctText = "withPattern: 1234-11-11";
    final String wrongText = "withPattern: <warning descr=\"Schema validation: String violates the pattern: '^\\d{4}\\-(0?[1-9]|1[012])\\-(0?[1-9]|[12][0-9]|3[01])$'\">wrong</warning>\n";
    doTestYaml(schema, correctText);
    doTestYaml(schema, wrongText);
  }

  // ---


  public void testRootObjectRedefinedAdditionalPropertiesForbidden() {
    doTestYaml(rootObjectRedefinedSchema(), "<warning descr=\"Schema validation: Property 'a' is not allowed\">a: true</warning>\n" +
                                        "r1: allowed!");
  }

  public void testNumberOfSameNamedPropertiesCorrectlyChecked() {
    @Language("YAML") final String schema = "{\n" +
                                            "  \"properties\": {\n" +
                                            "    \"size\": {\n" +
                                            "      \"type\": \"object\",\n" +
                                            "      \"minProperties\": 2,\n" +
                                            "      \"maxProperties\": 3,\n" +
                                            "      \"properties\": {\n" +
                                            "        \"a\": {\n" +
                                            "          \"type\": \"boolean\"\n" +
                                            "        }\n" +
                                            "      }\n" +
                                            "    }\n" +
                                            "  }\n" +
                                            "}";
    doTestYaml(schema,
           "size: \n" +
           " a: <warning descr=\"Schema validation: Incompatible types.\n Required: boolean. Actual: integer.\">1</warning>\n" +
           " b: 3\n" +
           " c: 4\n" +
           " a: <warning descr=\"Schema validation: Incompatible types.\n Required: boolean. Actual: integer.\">5</warning>" +
           "\n");
  }

  public void testManyDuplicatesInArray() {
    @Language("YAML") final String schema = "{\n" +
                                            "  \"properties\": {\n" +
                                            "    \"array\":{\n" +
                                            "      \"type\": \"array\",\n" +
                                            "      \"uniqueItems\": true\n" +
                                            "    }\n" +
                                            "  }\n" +
                                            "}";
    doTestYaml(schema, "array: \n" +
                   " - <warning descr=\"Schema validation: Item is not unique\">1</warning>\n" +
                   " - <warning descr=\"Schema validation: Item is not unique\">1</warning>\n" +
                   " - <warning descr=\"Schema validation: Item is not unique\">1</warning>\n" +
                   " - <warning descr=\"Schema validation: Item is not unique\">2</warning>\n" +
                   " - <warning descr=\"Schema validation: Item is not unique\">2</warning>\n" +
                   " - <warning descr=\"Schema validation: Item is not unique\">2</warning>\n" +
                   " - 5\n" +
                   " - <warning descr=\"Schema validation: Item is not unique\">3</warning>\n" +
                   " - <warning descr=\"Schema validation: Item is not unique\">3</warning>\n");
  }

  // ----

  public void testPropertyValueAlsoHighlightedIfPatternIsInvalid() {
    @Language("YAML") final String schema = "{\n" +
                                            "  \"properties\": {\n" +
                                            "    \"withPattern\": {\n" +
                                            "      \"pattern\": \"^[]$\"\n" +
                                            "    }\n" +
                                            "  }\n" +
                                            "}";
    final String text = "withPattern:" +
                        " <warning descr=\"Schema validation: Cannot check the string by pattern because of an error: Unclosed character class near index 3\n^[]$\n   ^\">(124)555-4216</warning>";
    doTestYaml(schema, text);
  }

  public void testNotSchema() {
    @Language("YAML") final String schema = "{\"properties\": {\n" +
                                            "    \"not_type\": { \"not\": { \"type\": \"string\" } }\n" +
                                            "  }}";
    doTestYaml(schema, "not_type: <warning descr=\"Schema validation: Validates against 'not' schema\">wrong</warning>");
  }

  public void testNotSchemaCombinedWithNormal() {
    @Language("YAML") final String schema = "{\"properties\": {\n" +
                                            "    \"not_type\": {\n" +
                                            "      \"pattern\": \"^[a-z]*[0-5]*$\",\n" +
                                            "      \"not\": { \"pattern\": \"^[a-z]{1}[0-5]$\" }\n" +
                                            "    }\n" +
                                            "  }}";
    doTestYaml(schema, "not_type: va4");
    doTestYaml(schema, "not_type: <warning descr=\"Schema validation: Validates against 'not' schema\">a4</warning>");
    doTestYaml(schema, "not_type: <warning descr=\"Schema validation: String violates the pattern: '^[a-z]*[0-5]*$'\">4a4</warning>");
  }

  public void testDoNotMarkOneOfThatDiffersWithFormat() {
    @Language("YAML") final String schema = "{\n" +
                                            "\n" +
                                            "  \"properties\": {\n" +
                                            "    \"withFormat\": {\n" +
                                            "      \"type\": \"string\"," +
                                            "      \"oneOf\": [\n" +
                                            "        {\n" +
                                            "          \"format\":\"hostname\"\n" +
                                            "        },\n" +
                                            "        {\n" +
                                            "          \"format\": \"ip4\"\n" +
                                            "        }\n" +
                                            "      ]\n" +
                                            "    }\n" +
                                            "  }\n" +
                                            "}";
    doTestYaml(schema, "withFormat: localhost");
  }

  public void testAcceptSchemaWithoutType() {
    @Language("YAML") final String schema = "{\n" +
                                            "\n" +
                                            "  \"properties\": {\n" +
                                            "    \"withFormat\": {\n" +
                                            "      \"oneOf\": [\n" +
                                            "        {\n" +
                                            "          \"format\":\"hostname\"\n" +
                                            "        },\n" +
                                            "        {\n" +
                                            "          \"format\": \"ip4\"\n" +
                                            "        }\n" +
                                            "      ]\n" +
                                            "    }\n" +
                                            "  }\n" +
                                            "}";
    doTestYaml(schema, "withFormat: localhost");
  }

  public void testArrayItemReference() {
    @Language("YAML") final String schema = "{\n" +
                                            "  \"items\": [\n" +
                                            "    {\n" +
                                            "      \"type\": \"integer\"\n" +
                                            "    },\n" +
                                            "    {\n" +
                                            "      \"$ref\": \"#/items/0\"\n" +
                                            "    }\n" +
                                            "  ]\n" +
                                            "}";
    doTestYaml(schema, "- 1\n- 2");
    doTestYaml(schema, "- 1\n- <warning>foo</warning>");
  }

  public void testValidateAdditionalItems() {
    @Language("YAML") final String schema = "{\n" +
                                            "  \"definitions\": {\n" +
                                            "    \"options\": {\n" +
                                            "      \"type\": \"array\",\n" +
                                            "      \"items\": {\n" +
                                            "        \"type\": \"number\"\n" +
                                            "      }\n" +
                                            "    }\n" +
                                            "  },\n" +
                                            "  \"items\": [\n" +
                                            "    {\n" +
                                            "      \"type\": \"boolean\"\n" +
                                            "    },\n" +
                                            "    {\n" +
                                            "      \"type\": \"boolean\"\n" +
                                            "    }\n" +
                                            "  ],\n" +
                                            "  \"additionalItems\": {\n" +
                                            "    \"$ref\": \"#/definitions/options/items\"\n" +
                                            "  }\n" +
                                            "}";
    doTestYaml(schema, "- true\n- true");
    doTestYaml(schema, "- true\n- true\n- 1\n- 2\n- 3");
    doTestYaml(schema, "- true\n- true\n- 1\n- <warning>qq</warning>");
  }



  public void testExclusiveMinMaxV6_1() {
    @Language("YAML") String exclusiveMinSchema = "{\"properties\": {\"prop\": {\"exclusiveMinimum\": 3}}}";
    doTestYaml(exclusiveMinSchema, "prop: <warning>2</warning>");
    doTestYaml(exclusiveMinSchema, "prop: <warning>3</warning>");
    doTestYaml(exclusiveMinSchema, "prop: 4");
  }

  public void testExclusiveMinMaxV6_2() {
    @Language("YAML") String exclusiveMaxSchema = "{\"properties\": {\"prop\": {\"exclusiveMaximum\": 3}}}";
    doTestYaml(exclusiveMaxSchema, "prop: 2");
    doTestYaml(exclusiveMaxSchema, "prop: <warning>3</warning>");
    doTestYaml(exclusiveMaxSchema, "prop: <warning>4</warning>");
  }

  /*todo later
  public void testPropertyNamesV6() throws Exception {
    doTestYaml("{\"propertyNames\": {\"minLength\": 7}}", "{<warning>\"prop\"</warning>: 2}");
    doTestYaml("{\"properties\": {\"prop\": {\"propertyNames\": {\"minLength\": 7}}}}", "{\"prop\": {<warning>\"qq\"</warning>: 7}}");
  }*/

  public void testContainsV6() {
    @Language("YAML") String schema = "{\"properties\": {\"prop\": {\"type\": \"array\", \"contains\": {\"type\": \"number\"}}}}";
    doTestYaml(schema, "prop:\n <warning>- a\n - true</warning>");
    doTestYaml(schema, "prop:\n - a\n - true\n - 1");
  }

  public void testConstV6() {
    @Language("YAML") String schema = "{\"properties\": {\"prop\": {\"type\": \"string\", \"const\": \"foo\"}}}";
    doTestYaml(schema, "prop: <warning>a</warning>");
    doTestYaml(schema, "prop: <warning>5</warning>");
    doTestYaml(schema, "prop: foo");
  }

  public void testIfThenElseV7() {
    @Language("YAML") String schema = "{\n" +
                                      "  \"if\": {\n" +
                                      "    \"properties\": {\n" +
                                      "      \"a\": {\n" +
                                      "        \"type\": \"string\"\n" +
                                      "      }\n" +
                                      "    },\n" +
                                      "    \"required\": [\"a\"]\n" +
                                      "  },\n" +
                                      "  \"then\": {\n" +
                                      "    \"properties\": {\n" +
                                      "      \"b\": {\n" +
                                      "        \"type\": \"number\"\n" +
                                      "      }\n" +
                                      "    },\n" +
                                      "    \"required\": [\"b\"]\n" +
                                      "  },\n" +
                                      "  \"else\": {\n" +
                                      "    \"properties\": {\n" +
                                      "      \"c\": {\n" +
                                      "        \"type\": \"boolean\"\n" +
                                      "      }\n" +
                                      "    },\n" +
                                      "    \"required\": [\"c\"]\n" +
                                      "  }\n" +
                                      "}";
    doTestYaml(schema, "c: <warning>5</warning>");
    doTestYaml(schema, "c: true");
    doTestYaml(schema, "<warning>a: a\nc: true</warning>");
    doTestYaml(schema, "a: a\nb: <warning>true</warning>");
    doTestYaml(schema, "a: a\nb: 5");
  }

  public void testNestedOneOf() {
    @Language("YAML") String schema = "{\"type\":\"object\",\n" +
                                      "  \"oneOf\": [\n" +
                                      "    {\n" +
                                      "      \"properties\": {\n" +
                                      "        \"type\": {\n" +
                                      "          \"type\": \"string\",\n" +
                                      "          \"oneOf\": [\n" +
                                      "            {\n" +
                                      "              \"pattern\": \"(good)\"\n" +
                                      "            },\n" +
                                      "            {\n" +
                                      "              \"pattern\": \"(ok)\"\n" +
                                      "            }\n" +
                                      "          ]\n" +
                                      "        }\n" +
                                      "      }\n" +
                                      "    },\n" +
                                      "    {\n" +
                                      "      \"properties\": {\n" +
                                      "        \"type\": {\n" +
                                      "          \"type\": \"string\",\n" +
                                      "          \"pattern\": \"^(fine)\"\n" +
                                      "        },\n" +
                                      "        \"extra\": {\n" +
                                      "          \"type\": \"string\"\n" +
                                      "        }\n" +
                                      "      },\n" +
                                      "      \"required\": [\"type\", \"extra\"]\n" +
                                      "    }\n" +
                                      "  ]}";

    doTestYaml(schema, "type: good");
    doTestYaml(schema, "type: ok");
    doTestYaml(schema, "type: <warning>doog</warning>");
    doTestYaml(schema, "type: <warning>ko</warning>");
  }

  public void testArrayRefs() {
    @Language("YAML") String schema = "{\n" +
                                      "  \"myDefs\": {\n" +
                                      "    \"myArray\": [\n" +
                                      "      {\n" +
                                      "        \"type\": \"number\"\n" +
                                      "      },\n" +
                                      "      {\n" +
                                      "        \"type\": \"boolean\"\n" +
                                      "      }\n" +
                                      "    ]\n" +
                                      "  },\n" +
                                      "  \"type\": \"array\",\n" +
                                      "  \"items\": [\n" +
                                      "    {\n" +
                                      "      \"$ref\": \"#/myDefs/myArray/0\"\n" +
                                      "    },\n" +
                                      "    {\n" +
                                      "      \"$ref\": \"#/myDefs/myArray/1\"\n" +
                                      "    }\n" +
                                      "  ]\n" +
                                      "}";

    doTestYaml(schema, "- 1\n- <warning>2</warning>");
    doTestYaml(schema, "- <warning>a</warning>\n- <warning>2</warning>");
    doTestYaml(schema, "- <warning>a</warning>\n- true");
    doTestYaml(schema, "- 1\n- false");
  }

  public void testWithTags() {
    @Language("YAML") String schema = "{\"properties\": { \"platform\": { \"enum\": [\"x86\", \"x64\"] } }}";
    doTestYaml(schema, "platform:\n  !!str x64");
    doTestYaml(schema, "platform:\n  <warning>a x64</warning>");
  }

  public void testAmazonElasticSchema() throws Exception {
    @Language("YAML") String schema = FileUtil.loadFile(new File(getTestDataPath() + "/cloudformation.schema.json"));
    doTestYaml(schema, "Resources:\n" +
                   "  ElasticsearchCluster:\n" +
                   "    Type: \"AWS::Elasticsearch::Domain\"\n" +
                   "    Properties:\n" +
                   "      ElasticsearchVersion: !FindInMap [ElasticSearchConfig, !Ref AccountType, Version]\n" +
                   "Conditions:\n" +
                   "  IsDev: !Equals [!Ref AccountType, dev]");
  }

  public void testGitlabSchema() throws Exception {
    @Language("YAML") String schema = FileUtil.loadFile(new File(getTestDataPath() + "/gitlab-ci.schema.json"));
    doTestYaml(schema, "a:\n" +
                   "  extends: .b\n" +
                   "  script: echo");
  }

  @Language("YAML")
  private static final String SCHEMA_FOR_REFS  = "{\n" +
                                                 "  \"type\": \"object\",\n" +
                                                 "\n" +
                                                 "  \"properties\": {\n" +
                                                 "    \"name\": { \"type\": \"string\", \"enum\": [\"aa\", \"bb\"] },\n" +
                                                 "    \"bar\": {\n" +
                                                 "      \"required\": [\n" +
                                                 "        \"a\"\n" +
                                                 "      ],\n" +
                                                 "      \"properties\": {\n" +
                                                 "        \"a\": {\n" +
                                                 "          \"type\": [\"array\"]\n" +
                                                 "        },\n" +
                                                 "       \"b\": {" +
                                                 "          \"type\": [\"number\"]" +
                                                 "        }\n" +
                                                 "      },\n" +
                                                 "      \"additionalProperties\": false\n" +
                                                 "    }\n" +
                                                 "  }\n" +
                                                 "}\n";

  public void testRefExtends() {
    // no warning about missing required property - it should be discovered in referenced object
    // no warning about extra 'property' with name '<<' with additionalProperties=false
    doTestYaml(SCHEMA_FOR_REFS, "a: &a\n" +
                            "  a: <warning descr=\"Schema validation: Incompatible types.\n Required: array. Actual: integer.\">7</warning>\n" +
                            "\n" +
                            "bar:\n" +
                            "  <<: *a\n" +
                            "  b: 5\n");
  }

  public void testRefRefValid() {
    // no warnings - &a references &b, which is an array - validation passes
    doTestYaml(SCHEMA_FOR_REFS, "x: &b\n" +
                            "  - x\n" +
                            "  - y\n" +
                            "\n" +
                            "a: &a\n" +
                            "  a: *b\n" +
                            "\n" +
                            "bar:\n" +
                            "  <<: *a\n" +
                            "  b: 5");
  }

  public void testRefRefInvalid() {
    doTestYaml(SCHEMA_FOR_REFS, "x: &b <warning descr=\"Schema validation: Incompatible types.\n Required: array. Actual: number.\">7</warning>\n" +
                            "\n" +
                            "a: &a\n" +
                            "  a: *b\n" +
                            "\n" +
                            "bar:\n" +
                            "  <<: *a\n" +
                            "  b: 5");
  }
  public void testRefRefScalarValid() {
    doTestYaml(SCHEMA_FOR_REFS, "x: &b 7\n" +
                            "\n" +
                            "a: &a\n" +
                            "  b: *b\n" +
                            "\n" +
                            "bar:\n" +
                            "  <<: *a\n" +
                            "  a: <warning descr=\"Schema validation: Incompatible types.\n Required: array. Actual: integer.\">5</warning>");
  }

  public void testInlineRef() {
    doTestYaml(SCHEMA_FOR_REFS, "bar:\n" +
                            "  <<: &q\n" +
                            "    a: <warning descr=\"Schema validation: Incompatible types.\n Required: array. Actual: integer.\">5</warning>\n" +
                            "  b: 5");
  }

  static String schema(final String s) {
    return "{\"type\": \"object\", \"properties\": {\"prop\": " + s + "}}";
  }

  public static String rootObjectRedefinedSchema() {
    return "{\n" +
           "  \"$schema\": \"http://json-schema.org/draft-04/schema#\",\n" +
           "  \"type\": \"object\",\n" +
           "  \"$ref\" : \"#/definitions/root\",\n" +
           "  \"definitions\": {\n" +
           "    \"root\" : {\n" +
           "      \"type\": \"object\",\n" +
           "      \"additionalProperties\": false,\n" +
           "      \"properties\": {\n" +
           "        \"r1\": {\n" +
           "          \"type\": \"string\"\n" +
           "        },\n" +
           "        \"r2\": {\n" +
           "          \"type\": \"string\"\n" +
           "        }\n" +
           "      }\n" +
           "    }\n" +
           "  }\n" +
           "}\n";
  }

  public void testTravisPythonVersion() throws Exception {
    @Language("YAML") String schema = FileUtil.loadFile(new File(getTestDataPath() + "/travis.schema.json"));
    doTestYaml(schema, "python: 3.5"); // validates as 'number'
    doTestYaml(schema, "python: 3.50"); // validates as 'number'
    doTestYaml(schema, "python: 3.50a"); // validates as 'string'
    doTestYaml(schema, "python: <warning descr=\"Schema validation: Incompatible types.\n Required one of: array, number, string. Actual: null.\">null</warning>");
  }

  public void testTravisNode() throws Exception {
    @Language("YAML") String schema = FileUtil.loadFile(new File(getTestDataPath() + "/travis.schema.json"));
    doTestYaml(schema, "node_js: \n" +
                   "  - <warning descr=\"Schema validation: Incompatible types.\n Required: string. Actual: number.\">2.10</warning>");
  }

  public void testExpNumberNotation() {
    doTestYaml("{\n" +
           "  \"properties\": {\n" +
           "    \"x\": {\n" +
           "      \"type\": \"number\"\n" +
           "    }\n" +
           "  }\n" +
           "}", "x: 2.99792458e8");
  }

  public void testTreatEmptyValueAsNull_1() {
    doTestYaml("{\n" +
               "  \"properties\": {\n" +
               "    \"x\": {\n" +
               "      \"type\": \"number\"\n" +
               "    }\n" +
               "  }\n" +
               "}", "x:<warning descr=\"Schema validation: Incompatible types.\n Required: number. Actual: null.\"> </warning>");
  }

  public void testTreatEmptyValueAsNull_2() {
    doTestYaml("{\n" +
           "  \"properties\": {\n" +
           "    \"x\": {\n" +
           "      \"type\": \"null\"\n" +
           "    }\n" +
           "  }\n" +
           "}", "x: ");
  }

  public void testEmptyValueInArray() {
    doTestYaml("{\n" +
           "  \"type\": \"object\",\n" +
           "\n" +
           "  \"properties\": {\n" +
           "    \"versionAsStringArray\": {\n" +
           "      \"type\": \"array\",\n" +
           "      \"items\": {\n" +
           "        \"type\": \"string\"\n" +
           "      }\n" +
           "    }\n" +
           "  }\n" +
           "}", "versionAsStringArray:\n" +
                "  -<warning descr=\"Schema validation: Incompatible types.\n Required: string. Actual: null.\"> </warning>\n" +
                "  <warning descr=\"Schema validation: Incompatible types.\n Required: string. Actual: null.\">-</warning>\n" +
                "  - a");
  }

  public void testEmptyFile() {
    doTestYaml("{\n" +
           "  \"type\": \"object\",\n" +
           "\n" +
           "  \"properties\": {\n" +
           "    \"versionAsStringArray\": {\n" +
           "      \"type\": \"array\"\n" +
           "    }\n" +
           "  },\n" +
           "  \"required\": [\"versionAsStringArray\"]\n" +
           "}", "<warning descr=\"Schema validation: Missing required property 'versionAsStringArray'\"></warning>");
  }

  public void testEmptyValueBetweenProps() {
    doTestYaml("{\n" +
           "  \"type\": \"object\",\n" +
           "\n" +
           "  \"properties\": {\n" +
           "    \"versionAsStringArray\": {\n" +
           "      \"type\": \"object\",\n" +
           "      \"properties\": {\n" +
           "        \"xxx\": {\n" +
           "          \"type\": \"number\"\n" +
           "        },\n" +
           "        \"yyy\": {\n" +
           "          \"type\": \"string\"\n" +
           "        },\n" +
           "        \"zzz\": {\n" +
           "          \"type\": \"number\"\n" +
           "        }\n" +
           "      },\n" +
           "      \"required\": [\"xxx\", \"yyy\", \"zzz\"]\n" +
           "    }\n" +
           "  },\n" +
           "  \"required\": [\"versionAsStringArray\"]\n" +
           "}", "versionAsStringArray:\n" +
                "  zzz: 0\n" +
                "  yyy:<warning descr=\"Schema validation: Incompatible types.\n Required: string. Actual: null.\">  </warning>\n" +
                "  xxx: 0");
  }

  public void testDeprecation() {
    doTestYaml("{\"properties\": {\n" +
           "    \"myPropertyXxx\": {\n" +
           "      \"deprecationMessage\": \"Baz\",\n" +
           "      \"description\": \"Foo bar\"\n" +
           "    }\n" +
           "  }}", "<weak_warning descr=\"Key 'myPropertyXxx' is deprecated: Baz\">myPropertyXxx</weak_warning>: a");
  }
}
