/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.iceberg.spark;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.expressions.Expressions;
import org.apache.iceberg.expressions.UnboundTerm;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.spark.functions.BucketFunction;
import org.apache.iceberg.spark.functions.DaysFunction;
import org.apache.iceberg.spark.functions.HoursFunction;
import org.apache.iceberg.spark.functions.MonthsFunction;
import org.apache.iceberg.spark.functions.TruncateFunction;
import org.apache.iceberg.spark.functions.YearsFunction;
import org.apache.spark.sql.connector.catalog.functions.ScalarFunction;
import org.apache.spark.sql.connector.expressions.FieldReference;
import org.apache.spark.sql.connector.expressions.LiteralValue;
import org.apache.spark.sql.connector.expressions.NamedReference;
import org.apache.spark.sql.connector.expressions.UserDefinedScalarFunc;
import org.apache.spark.sql.connector.expressions.filter.And;
import org.apache.spark.sql.connector.expressions.filter.Not;
import org.apache.spark.sql.connector.expressions.filter.Or;
import org.apache.spark.sql.connector.expressions.filter.Predicate;
import org.apache.spark.sql.types.DataType;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.unsafe.types.UTF8String;
import org.assertj.core.api.Assertions;
import org.junit.Assert;
import org.junit.Test;

public class TestSparkV2Filters {

  @SuppressWarnings("checkstyle:MethodLength")
  @Test
  public void testV2Filters() {
    Map<String, String> attrMap = Maps.newHashMap();
    attrMap.put("id", "id");
    attrMap.put("`i.d`", "i.d");
    attrMap.put("`i``d`", "i`d");
    attrMap.put("`d`.b.`dd```", "d.b.dd`");
    attrMap.put("a.`aa```.c", "a.aa`.c");

    attrMap.forEach(
        (quoted, unquoted) -> {
          NamedReference namedReference = FieldReference.apply(quoted);
          org.apache.spark.sql.connector.expressions.Expression[] attrOnly =
              new org.apache.spark.sql.connector.expressions.Expression[] {namedReference};

          LiteralValue value = new LiteralValue(1, DataTypes.IntegerType);
          org.apache.spark.sql.connector.expressions.Expression[] attrAndValue =
              new org.apache.spark.sql.connector.expressions.Expression[] {namedReference, value};
          org.apache.spark.sql.connector.expressions.Expression[] valueAndAttr =
              new org.apache.spark.sql.connector.expressions.Expression[] {value, namedReference};

          Predicate isNull = new Predicate("IS_NULL", attrOnly);
          Expression expectedIsNull = Expressions.isNull(unquoted);
          Expression actualIsNull = SparkV2Filters.convert(isNull);
          Assert.assertEquals(
              "IsNull must match", expectedIsNull.toString(), actualIsNull.toString());

          Predicate isNotNull = new Predicate("IS_NOT_NULL", attrOnly);
          Expression expectedIsNotNull = Expressions.notNull(unquoted);
          Expression actualIsNotNull = SparkV2Filters.convert(isNotNull);
          Assert.assertEquals(
              "IsNotNull must match", expectedIsNotNull.toString(), actualIsNotNull.toString());

          Predicate lt1 = new Predicate("<", attrAndValue);
          Expression expectedLt1 = Expressions.lessThan(unquoted, 1);
          Expression actualLt1 = SparkV2Filters.convert(lt1);
          Assert.assertEquals("LessThan must match", expectedLt1.toString(), actualLt1.toString());

          Predicate lt2 = new Predicate("<", valueAndAttr);
          Expression expectedLt2 = Expressions.greaterThan(unquoted, 1);
          Expression actualLt2 = SparkV2Filters.convert(lt2);
          Assert.assertEquals("LessThan must match", expectedLt2.toString(), actualLt2.toString());

          Predicate ltEq1 = new Predicate("<=", attrAndValue);
          Expression expectedLtEq1 = Expressions.lessThanOrEqual(unquoted, 1);
          Expression actualLtEq1 = SparkV2Filters.convert(ltEq1);
          Assert.assertEquals(
              "LessThanOrEqual must match", expectedLtEq1.toString(), actualLtEq1.toString());

          Predicate ltEq2 = new Predicate("<=", valueAndAttr);
          Expression expectedLtEq2 = Expressions.greaterThanOrEqual(unquoted, 1);
          Expression actualLtEq2 = SparkV2Filters.convert(ltEq2);
          Assert.assertEquals(
              "LessThanOrEqual must match", expectedLtEq2.toString(), actualLtEq2.toString());

          Predicate gt1 = new Predicate(">", attrAndValue);
          Expression expectedGt1 = Expressions.greaterThan(unquoted, 1);
          Expression actualGt1 = SparkV2Filters.convert(gt1);
          Assert.assertEquals(
              "GreaterThan must match", expectedGt1.toString(), actualGt1.toString());

          Predicate gt2 = new Predicate(">", valueAndAttr);
          Expression expectedGt2 = Expressions.lessThan(unquoted, 1);
          Expression actualGt2 = SparkV2Filters.convert(gt2);
          Assert.assertEquals(
              "GreaterThan must match", expectedGt2.toString(), actualGt2.toString());

          Predicate gtEq1 = new Predicate(">=", attrAndValue);
          Expression expectedGtEq1 = Expressions.greaterThanOrEqual(unquoted, 1);
          Expression actualGtEq1 = SparkV2Filters.convert(gtEq1);
          Assert.assertEquals(
              "GreaterThanOrEqual must match", expectedGtEq1.toString(), actualGtEq1.toString());

          Predicate gtEq2 = new Predicate(">=", valueAndAttr);
          Expression expectedGtEq2 = Expressions.lessThanOrEqual(unquoted, 1);
          Expression actualGtEq2 = SparkV2Filters.convert(gtEq2);
          Assert.assertEquals(
              "GreaterThanOrEqual must match", expectedGtEq2.toString(), actualGtEq2.toString());

          Predicate eq1 = new Predicate("=", attrAndValue);
          Expression expectedEq1 = Expressions.equal(unquoted, 1);
          Expression actualEq1 = SparkV2Filters.convert(eq1);
          Assert.assertEquals("EqualTo must match", expectedEq1.toString(), actualEq1.toString());

          Predicate eq2 = new Predicate("=", valueAndAttr);
          Expression expectedEq2 = Expressions.equal(unquoted, 1);
          Expression actualEq2 = SparkV2Filters.convert(eq2);
          Assert.assertEquals("EqualTo must match", expectedEq2.toString(), actualEq2.toString());

          Predicate notEq1 = new Predicate("<>", attrAndValue);
          Expression expectedNotEq1 = Expressions.notEqual(unquoted, 1);
          Expression actualNotEq1 = SparkV2Filters.convert(notEq1);
          Assert.assertEquals(
              "NotEqualTo must match", expectedNotEq1.toString(), actualNotEq1.toString());

          Predicate notEq2 = new Predicate("<>", valueAndAttr);
          Expression expectedNotEq2 = Expressions.notEqual(unquoted, 1);
          Expression actualNotEq2 = SparkV2Filters.convert(notEq2);
          Assert.assertEquals(
              "NotEqualTo must match", expectedNotEq2.toString(), actualNotEq2.toString());

          Predicate eqNullSafe1 = new Predicate("<=>", attrAndValue);
          Expression expectedEqNullSafe1 = Expressions.equal(unquoted, 1);
          Expression actualEqNullSafe1 = SparkV2Filters.convert(eqNullSafe1);
          Assert.assertEquals(
              "EqualNullSafe must match",
              expectedEqNullSafe1.toString(),
              actualEqNullSafe1.toString());

          Predicate eqNullSafe2 = new Predicate("<=>", valueAndAttr);
          Expression expectedEqNullSafe2 = Expressions.equal(unquoted, 1);
          Expression actualEqNullSafe2 = SparkV2Filters.convert(eqNullSafe2);
          Assert.assertEquals(
              "EqualNullSafe must match",
              expectedEqNullSafe2.toString(),
              actualEqNullSafe2.toString());

          LiteralValue str =
              new LiteralValue(UTF8String.fromString("iceberg"), DataTypes.StringType);
          org.apache.spark.sql.connector.expressions.Expression[] attrAndStr =
              new org.apache.spark.sql.connector.expressions.Expression[] {namedReference, str};
          Predicate startsWith = new Predicate("STARTS_WITH", attrAndStr);
          Expression expectedStartsWith = Expressions.startsWith(unquoted, "iceberg");
          Expression actualStartsWith = SparkV2Filters.convert(startsWith);
          Assert.assertEquals(
              "StartsWith must match", expectedStartsWith.toString(), actualStartsWith.toString());

          Predicate in = new Predicate("IN", attrAndValue);
          Expression expectedIn = Expressions.in(unquoted, 1);
          Expression actualIn = SparkV2Filters.convert(in);
          Assert.assertEquals("In must match", expectedIn.toString(), actualIn.toString());

          Predicate and = new And(lt1, eq1);
          Expression expectedAnd = Expressions.and(expectedLt1, expectedEq1);
          Expression actualAnd = SparkV2Filters.convert(and);
          Assert.assertEquals("And must match", expectedAnd.toString(), actualAnd.toString());

          org.apache.spark.sql.connector.expressions.Expression[] attrAndAttr =
              new org.apache.spark.sql.connector.expressions.Expression[] {
                namedReference, namedReference
              };
          Predicate invalid = new Predicate("<", attrAndAttr);
          Predicate andWithInvalidLeft = new And(invalid, eq1);
          Expression convertedAnd = SparkV2Filters.convert(andWithInvalidLeft);
          Assert.assertEquals("And must match", convertedAnd, null);

          Predicate or = new Or(lt1, eq1);
          Expression expectedOr = Expressions.or(expectedLt1, expectedEq1);
          Expression actualOr = SparkV2Filters.convert(or);
          Assert.assertEquals("Or must match", expectedOr.toString(), actualOr.toString());

          Predicate orWithInvalidLeft = new Or(invalid, eq1);
          Expression convertedOr = SparkV2Filters.convert(orWithInvalidLeft);
          Assert.assertEquals("Or must match", convertedOr, null);

          Predicate not = new Not(lt1);
          Expression expectedNot = Expressions.not(expectedLt1);
          Expression actualNot = SparkV2Filters.convert(not);
          Assert.assertEquals("Not must match", expectedNot.toString(), actualNot.toString());
        });
  }

  @Test
  public void testEqualToNull() {
    String col = "col";
    NamedReference namedReference = FieldReference.apply(col);
    LiteralValue value = new LiteralValue(null, DataTypes.IntegerType);

    org.apache.spark.sql.connector.expressions.Expression[] attrAndValue =
        new org.apache.spark.sql.connector.expressions.Expression[] {namedReference, value};
    org.apache.spark.sql.connector.expressions.Expression[] valueAndAttr =
        new org.apache.spark.sql.connector.expressions.Expression[] {value, namedReference};

    Predicate eq1 = new Predicate("=", attrAndValue);
    Assertions.assertThatThrownBy(() -> SparkV2Filters.convert(eq1))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("Expression is always false");

    Predicate eq2 = new Predicate("=", valueAndAttr);
    Assertions.assertThatThrownBy(() -> SparkV2Filters.convert(eq2))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("Expression is always false");

    Predicate eqNullSafe1 = new Predicate("<=>", attrAndValue);
    Expression expectedEqNullSafe = Expressions.isNull(col);
    Expression actualEqNullSafe1 = SparkV2Filters.convert(eqNullSafe1);
    Assertions.assertThat(actualEqNullSafe1.toString()).isEqualTo(expectedEqNullSafe.toString());

    Predicate eqNullSafe2 = new Predicate("<=>", valueAndAttr);
    Expression actualEqNullSafe2 = SparkV2Filters.convert(eqNullSafe2);
    Assertions.assertThat(actualEqNullSafe2.toString()).isEqualTo(expectedEqNullSafe.toString());
  }

  @Test
  public void testEqualToNaN() {
    String col = "col";
    NamedReference namedReference = FieldReference.apply(col);
    LiteralValue value = new LiteralValue(Float.NaN, DataTypes.FloatType);

    org.apache.spark.sql.connector.expressions.Expression[] attrAndValue =
        new org.apache.spark.sql.connector.expressions.Expression[] {namedReference, value};
    org.apache.spark.sql.connector.expressions.Expression[] valueAndAttr =
        new org.apache.spark.sql.connector.expressions.Expression[] {value, namedReference};

    Predicate eqNaN1 = new Predicate("=", attrAndValue);
    Expression expectedEqNaN = Expressions.isNaN(col);
    Expression actualEqNaN1 = SparkV2Filters.convert(eqNaN1);
    Assertions.assertThat(actualEqNaN1.toString()).isEqualTo(expectedEqNaN.toString());

    Predicate eqNaN2 = new Predicate("=", valueAndAttr);
    Expression actualEqNaN2 = SparkV2Filters.convert(eqNaN2);
    Assertions.assertThat(actualEqNaN2.toString()).isEqualTo(expectedEqNaN.toString());
  }

  @Test
  public void testNotEqualToNull() {
    String col = "col";
    NamedReference namedReference = FieldReference.apply(col);
    LiteralValue value = new LiteralValue(null, DataTypes.IntegerType);

    org.apache.spark.sql.connector.expressions.Expression[] attrAndValue =
        new org.apache.spark.sql.connector.expressions.Expression[] {namedReference, value};
    org.apache.spark.sql.connector.expressions.Expression[] valueAndAttr =
        new org.apache.spark.sql.connector.expressions.Expression[] {value, namedReference};

    Predicate notEq1 = new Predicate("<>", attrAndValue);
    Assertions.assertThatThrownBy(() -> SparkV2Filters.convert(notEq1))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("Expression is always false");

    Predicate notEq2 = new Predicate("<>", valueAndAttr);
    Assertions.assertThatThrownBy(() -> SparkV2Filters.convert(notEq2))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("Expression is always false");
  }

  @Test
  public void testNotEqualToNaN() {
    String col = "col";
    NamedReference namedReference = FieldReference.apply(col);
    LiteralValue value = new LiteralValue(Float.NaN, DataTypes.FloatType);

    org.apache.spark.sql.connector.expressions.Expression[] attrAndValue =
        new org.apache.spark.sql.connector.expressions.Expression[] {namedReference, value};
    org.apache.spark.sql.connector.expressions.Expression[] valueAndAttr =
        new org.apache.spark.sql.connector.expressions.Expression[] {value, namedReference};

    Predicate notEqNaN1 = new Predicate("<>", attrAndValue);
    Expression expectedNotEqNaN = Expressions.notNaN(col);
    Expression actualNotEqNaN1 = SparkV2Filters.convert(notEqNaN1);
    Assertions.assertThat(actualNotEqNaN1.toString()).isEqualTo(expectedNotEqNaN.toString());

    Predicate notEqNaN2 = new Predicate("<>", valueAndAttr);
    Expression actualNotEqNaN2 = SparkV2Filters.convert(notEqNaN2);
    Assertions.assertThat(actualNotEqNaN2.toString()).isEqualTo(expectedNotEqNaN.toString());
  }

  @Test
  public void testTimestampFilterConversion() {
    Instant instant = Instant.parse("2018-10-18T00:00:57.907Z");
    long epochMicros = ChronoUnit.MICROS.between(Instant.EPOCH, instant);

    NamedReference namedReference = FieldReference.apply("x");
    LiteralValue ts = new LiteralValue(epochMicros, DataTypes.TimestampType);
    org.apache.spark.sql.connector.expressions.Expression[] attrAndValue =
        new org.apache.spark.sql.connector.expressions.Expression[] {namedReference, ts};

    Predicate predicate = new Predicate(">", attrAndValue);
    Expression tsExpression = SparkV2Filters.convert(predicate);
    Expression rawExpression = Expressions.greaterThan("x", epochMicros);

    Assert.assertEquals(
        "Generated Timestamp expression should be correct",
        rawExpression.toString(),
        tsExpression.toString());
  }

  @Test
  public void testDateFilterConversion() {
    LocalDate localDate = LocalDate.parse("2018-10-18");
    long epochDay = localDate.toEpochDay();

    NamedReference namedReference = FieldReference.apply("x");
    LiteralValue ts = new LiteralValue(epochDay, DataTypes.DateType);
    org.apache.spark.sql.connector.expressions.Expression[] attrAndValue =
        new org.apache.spark.sql.connector.expressions.Expression[] {namedReference, ts};

    Predicate predicate = new Predicate(">", attrAndValue);
    Expression dateExpression = SparkV2Filters.convert(predicate);
    Expression rawExpression = Expressions.greaterThan("x", epochDay);

    Assert.assertEquals(
        "Generated date expression should be correct",
        rawExpression.toString(),
        dateExpression.toString());
  }

  @Test
  public void testNestedInInsideNot() {
    NamedReference namedReference1 = FieldReference.apply("col1");
    LiteralValue v1 = new LiteralValue(1, DataTypes.IntegerType);
    LiteralValue v2 = new LiteralValue(2, DataTypes.IntegerType);
    org.apache.spark.sql.connector.expressions.Expression[] attrAndValue1 =
        new org.apache.spark.sql.connector.expressions.Expression[] {namedReference1, v1};
    Predicate equal = new Predicate("=", attrAndValue1);

    NamedReference namedReference2 = FieldReference.apply("col2");
    org.apache.spark.sql.connector.expressions.Expression[] attrAndValue2 =
        new org.apache.spark.sql.connector.expressions.Expression[] {namedReference2, v1, v2};
    Predicate in = new Predicate("IN", attrAndValue2);

    Not filter = new Not(new And(equal, in));
    Expression converted = SparkV2Filters.convert(filter);
    Assert.assertNull("Expression should not be converted", converted);
  }

  @Test
  public void testNotIn() {
    NamedReference namedReference = FieldReference.apply("col");
    LiteralValue v1 = new LiteralValue(1, DataTypes.IntegerType);
    LiteralValue v2 = new LiteralValue(2, DataTypes.IntegerType);
    org.apache.spark.sql.connector.expressions.Expression[] attrAndValue =
        new org.apache.spark.sql.connector.expressions.Expression[] {namedReference, v1, v2};

    Predicate in = new Predicate("IN", attrAndValue);
    Not not = new Not(in);

    Expression actual = SparkV2Filters.convert(not);
    Expression expected =
        Expressions.and(Expressions.notNull("col"), Expressions.notIn("col", 1, 2));
    Assert.assertEquals("Expressions should match", expected.toString(), actual.toString());
  }

  @Test
  public void testYear() {
    ScalarFunction<Integer> dateToYear = new YearsFunction.DateToYearsFunction();
    UserDefinedScalarFunc udf =
        new UserDefinedScalarFunc(
            dateToYear.name(),
            dateToYear.canonicalName(),
            expressions(FieldReference.apply("col1")));
    testUDF(udf, Expressions.year("col1"), 2021, DataTypes.IntegerType);
  }

  @Test
  public void testMonth() {
    ScalarFunction<Integer> dateToMonth = new MonthsFunction.DateToMonthsFunction();
    UserDefinedScalarFunc udf =
        new UserDefinedScalarFunc(
            dateToMonth.name(),
            dateToMonth.canonicalName(),
            expressions(FieldReference.apply("col1")));
    testUDF(udf, Expressions.month("col1"), 5, DataTypes.IntegerType);
  }

  @Test
  public void testDay() {
    ScalarFunction<Integer> dateToDay = new DaysFunction.DateToDaysFunction();
    UserDefinedScalarFunc udf =
        new UserDefinedScalarFunc(
            dateToDay.name(), dateToDay.canonicalName(), expressions(FieldReference.apply("col1")));
    testUDF(udf, Expressions.day("col1"), 20, DataTypes.IntegerType);
  }

  @Test
  public void testHour() {
    ScalarFunction<Integer> tsToHour = new HoursFunction.TimestampToHoursFunction();
    UserDefinedScalarFunc udf =
        new UserDefinedScalarFunc(
            tsToHour.name(), tsToHour.canonicalName(), expressions(FieldReference.apply("col1")));
    testUDF(udf, Expressions.hour("col1"), 20, DataTypes.IntegerType);
  }

  @Test
  public void testBucket() {
    ScalarFunction<Integer> bucketInt = new BucketFunction.BucketInt(DataTypes.IntegerType);
    UserDefinedScalarFunc udf =
        new UserDefinedScalarFunc(
            bucketInt.name(),
            bucketInt.canonicalName(),
            expressions(
                LiteralValue.apply(4, DataTypes.IntegerType), FieldReference.apply("col1")));
    testUDF(udf, Expressions.bucket("col1", 4), 2, DataTypes.IntegerType);
  }

  @Test
  public void testTruncate() {
    ScalarFunction<UTF8String> truncate = new TruncateFunction.TruncateString();
    UserDefinedScalarFunc udf =
        new UserDefinedScalarFunc(
            truncate.name(),
            truncate.canonicalName(),
            expressions(
                LiteralValue.apply(6, DataTypes.IntegerType), FieldReference.apply("col1")));
    testUDF(udf, Expressions.truncate("col1", 6), "prefix", DataTypes.StringType);
  }

  private <T> void testUDF(
      org.apache.spark.sql.connector.expressions.Expression expression,
      UnboundTerm<T> item,
      T value,
      DataType dataType) {
    org.apache.spark.sql.connector.expressions.Expression[] attrOnly = expressions(expression);

    LiteralValue literalValue = new LiteralValue(value, dataType);
    org.apache.spark.sql.connector.expressions.Expression[] attrAndValue =
        expressions(expression, literalValue);
    org.apache.spark.sql.connector.expressions.Expression[] valueAndAttr =
        expressions(literalValue, expression);

    Predicate isNull = new Predicate("IS_NULL", attrOnly);
    Expression expectedIsNull = Expressions.isNull(item);
    Expression actualIsNull = SparkV2Filters.convert(isNull);
    Assertions.assertThat(actualIsNull.toString()).isEqualTo(expectedIsNull.toString());

    Predicate isNotNull = new Predicate("IS_NOT_NULL", attrOnly);
    Expression expectedIsNotNull = Expressions.notNull(item);
    Expression actualIsNotNull = SparkV2Filters.convert(isNotNull);
    Assertions.assertThat(actualIsNotNull.toString()).isEqualTo(expectedIsNotNull.toString());

    Predicate lt1 = new Predicate("<", attrAndValue);
    Expression expectedLt1 = Expressions.lessThan(item, value);
    Expression actualLt1 = SparkV2Filters.convert(lt1);
    Assertions.assertThat(actualLt1.toString()).isEqualTo(expectedLt1.toString());

    Predicate lt2 = new Predicate("<", valueAndAttr);
    Expression expectedLt2 = Expressions.greaterThan(item, value);
    Expression actualLt2 = SparkV2Filters.convert(lt2);
    Assertions.assertThat(actualLt2.toString()).isEqualTo(expectedLt2.toString());

    Predicate ltEq1 = new Predicate("<=", attrAndValue);
    Expression expectedLtEq1 = Expressions.lessThanOrEqual(item, value);
    Expression actualLtEq1 = SparkV2Filters.convert(ltEq1);
    Assertions.assertThat(actualLtEq1.toString()).isEqualTo(expectedLtEq1.toString());

    Predicate ltEq2 = new Predicate("<=", valueAndAttr);
    Expression expectedLtEq2 = Expressions.greaterThanOrEqual(item, value);
    Expression actualLtEq2 = SparkV2Filters.convert(ltEq2);
    Assertions.assertThat(actualLtEq2.toString()).isEqualTo(expectedLtEq2.toString());

    Predicate gt1 = new Predicate(">", attrAndValue);
    Expression expectedGt1 = Expressions.greaterThan(item, value);
    Expression actualGt1 = SparkV2Filters.convert(gt1);
    Assertions.assertThat(actualGt1.toString()).isEqualTo(expectedGt1.toString());

    Predicate gt2 = new Predicate(">", valueAndAttr);
    Expression expectedGt2 = Expressions.lessThan(item, value);
    Expression actualGt2 = SparkV2Filters.convert(gt2);
    Assertions.assertThat(actualGt2.toString()).isEqualTo(expectedGt2.toString());

    Predicate gtEq1 = new Predicate(">=", attrAndValue);
    Expression expectedGtEq1 = Expressions.greaterThanOrEqual(item, value);
    Expression actualGtEq1 = SparkV2Filters.convert(gtEq1);
    Assertions.assertThat(actualGtEq1.toString()).isEqualTo(expectedGtEq1.toString());

    Predicate gtEq2 = new Predicate(">=", valueAndAttr);
    Expression expectedGtEq2 = Expressions.lessThanOrEqual(item, value);
    Expression actualGtEq2 = SparkV2Filters.convert(gtEq2);
    Assertions.assertThat(actualGtEq2.toString()).isEqualTo(expectedGtEq2.toString());

    Predicate eq1 = new Predicate("=", attrAndValue);
    Expression expectedEq1 = Expressions.equal(item, value);
    Expression actualEq1 = SparkV2Filters.convert(eq1);
    Assertions.assertThat(actualEq1.toString()).isEqualTo(expectedEq1.toString());

    Predicate eq2 = new Predicate("=", valueAndAttr);
    Expression expectedEq2 = Expressions.equal(item, value);
    Expression actualEq2 = SparkV2Filters.convert(eq2);
    Assertions.assertThat(actualEq2.toString()).isEqualTo(expectedEq2.toString());

    Predicate notEq1 = new Predicate("<>", attrAndValue);
    Expression expectedNotEq1 = Expressions.notEqual(item, value);
    Expression actualNotEq1 = SparkV2Filters.convert(notEq1);
    Assertions.assertThat(actualNotEq1.toString()).isEqualTo(expectedNotEq1.toString());

    Predicate notEq2 = new Predicate("<>", valueAndAttr);
    Expression expectedNotEq2 = Expressions.notEqual(item, value);
    Expression actualNotEq2 = SparkV2Filters.convert(notEq2);
    Assertions.assertThat(actualNotEq2.toString()).isEqualTo(expectedNotEq2.toString());

    Predicate eqNullSafe1 = new Predicate("<=>", attrAndValue);
    Expression expectedEqNullSafe1 = Expressions.equal(item, value);
    Expression actualEqNullSafe1 = SparkV2Filters.convert(eqNullSafe1);
    Assertions.assertThat(actualEqNullSafe1.toString()).isEqualTo(expectedEqNullSafe1.toString());

    Predicate eqNullSafe2 = new Predicate("<=>", valueAndAttr);
    Expression expectedEqNullSafe2 = Expressions.equal(item, value);
    Expression actualEqNullSafe2 = SparkV2Filters.convert(eqNullSafe2);
    Assertions.assertThat(actualEqNullSafe2.toString()).isEqualTo(expectedEqNullSafe2.toString());

    Predicate in = new Predicate("IN", attrAndValue);
    Expression expectedIn = Expressions.in(item, value);
    Expression actualIn = SparkV2Filters.convert(in);
    Assertions.assertThat(actualIn.toString()).isEqualTo(expectedIn.toString());

    Predicate and = new And(lt1, eq1);
    Expression expectedAnd = Expressions.and(expectedLt1, expectedEq1);
    Expression actualAnd = SparkV2Filters.convert(and);
    Assertions.assertThat(actualAnd.toString()).isEqualTo(expectedAnd.toString());

    org.apache.spark.sql.connector.expressions.Expression[] attrAndAttr =
        expressions(expression, expression);
    Predicate invalid = new Predicate("<", attrAndAttr);
    Predicate andWithInvalidLeft = new And(invalid, eq1);
    Expression convertedAnd = SparkV2Filters.convert(andWithInvalidLeft);
    Assertions.assertThat(convertedAnd).isNull();

    Predicate or = new Or(lt1, eq1);
    Expression expectedOr = Expressions.or(expectedLt1, expectedEq1);
    Expression actualOr = SparkV2Filters.convert(or);
    Assertions.assertThat(actualOr.toString()).isEqualTo(expectedOr.toString());

    Predicate orWithInvalidLeft = new Or(invalid, eq1);
    Expression convertedOr = SparkV2Filters.convert(orWithInvalidLeft);
    Assertions.assertThat(convertedOr).isNull();

    Predicate not = new Not(lt1);
    Expression expectedNot = Expressions.not(expectedLt1);
    Expression actualNot = SparkV2Filters.convert(not);
    Assertions.assertThat(actualNot.toString()).isEqualTo(expectedNot.toString());
  }

  private org.apache.spark.sql.connector.expressions.Expression[] expressions(
      org.apache.spark.sql.connector.expressions.Expression... expressions) {
    return expressions;
  }
}
