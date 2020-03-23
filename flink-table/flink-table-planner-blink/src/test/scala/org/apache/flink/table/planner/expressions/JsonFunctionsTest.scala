/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.table.planner.expressions

import org.apache.flink.api.common.typeinfo.Types
import org.apache.flink.api.java.typeutils.RowTypeInfo
import org.apache.flink.table.api.ValidationException
import org.apache.flink.table.planner.codegen.CodeGenException
import org.apache.flink.table.planner.expressions.utils.ExpressionTestBase
import org.apache.flink.types.Row
import org.junit.Assert.assertEquals
import org.junit.Test

class JsonFunctionsTest extends ExpressionTestBase {

  override def testData: Row = {
    val testData = new Row(9)
    testData.setField(0, "This is a test String.")
    testData.setField(1, true)
    testData.setField(2, 42.toByte)
    testData.setField(3, 43.toShort)
    testData.setField(4, 44.toLong)
    testData.setField(5, 4.5.toFloat)
    testData.setField(6, 4.6)
    testData.setField(7, 3)
    testData.setField(8, """{ "name" : "flink" }""")
    testData
  }

  override def typeInfo: RowTypeInfo = {
    new RowTypeInfo(
      /* 0 */  Types.STRING,
      /* 1 */  Types.BOOLEAN,
      /* 2 */  Types.BYTE,
      /* 3 */  Types.SHORT,
      /* 4 */  Types.LONG,
      /* 5 */  Types.FLOAT,
      /* 6 */  Types.DOUBLE,
      /* 7 */  Types.INT,
      /* 8 */  Types.STRING)
  }

  @Test
  def testPredicates(): Unit = {
    val malformed = Array(false, false, false, false)
    val jsonObject = Array(true, true, false, false)
    val jsonArray = Array(true, false, true, false)
    val jsonScalar = Array(true, false, false, true)

    // strings
    verifyPredicates("'{}'", jsonObject)
    verifyPredicates("'[]'", jsonArray)
    verifyPredicates("'100'", jsonScalar)
    verifyPredicates("'{]'", malformed)

    // valid fields
    verifyPredicates("f0", malformed)
    verifyPredicates("f8", jsonObject)

    // invalid fields
    verifyException("f1", classOf[ValidationException])
    verifyException("f2", classOf[ValidationException])
    verifyException("f3", classOf[ValidationException])
    verifyException("f4", classOf[ValidationException])
    verifyException("f5", classOf[ValidationException])
    verifyException("f6", classOf[ValidationException])
    verifyException("f7", classOf[ValidationException])
  }

  /**
   * Utility for verify predicates.
   *
   * @param candidate to be verified, can be a scalar or a column
   * @param expectedValues array of expected values as result of
   *                       (IS_JSON_VALUE, IS_JSON_OBJECT, IS_JSON_ARRAY, IS_JSON_SCALAR)
   */
  private def verifyPredicates(candidate: String, expectedValues: Array[Boolean]): Unit = {
    assert(expectedValues.length == 4)

    testSqlApi(s"$candidate is json value", expectedValues(0).toString)
    testSqlApi(s"$candidate is not json value", (!expectedValues(0)).toString)
    testSqlApi(s"$candidate is json object", expectedValues(1).toString)
    testSqlApi(s"$candidate is not json object", (!expectedValues(1)).toString)
    testSqlApi(s"$candidate is json array", expectedValues(2).toString)
    testSqlApi(s"$candidate is not json array", (!expectedValues(2)).toString)
    testSqlApi(s"$candidate is json scalar", expectedValues(3).toString)
    testSqlApi(s"$candidate is not json scalar", (!expectedValues(3)).toString)
  }

  private def verifyException[T <: Exception](
    candidate: String,
    expectedException: Class[T]
  ): Unit = {
    val sqlCandidates = Array(
      s"$candidate is json value",
      s"$candidate is not json value",
      s"$candidate is json object",
      s"$candidate is not json object",
      s"$candidate is json array",
      s"$candidate is not json array",
      s"$candidate is json scalar",
      s"$candidate is not json scalar")

    for (sql <- sqlCandidates) {
      try {
        testSqlApi(sql, "null")
      } catch {
        case e: Exception => assertEquals(e.getClass, expectedException)
      }
    }
  }

  @Test
  def testJsonQuery(): Unit = {
    // lax test
    testSqlApi("json_query('{\"foo\":100}', 'lax $' null on empty)", "{\"foo\":100}")
    testSqlApi("json_query('{\"foo\":100}', 'lax $' error on empty)", "{\"foo\":100}")
    testSqlApi("json_query('{\"foo\":100}', 'lax $' empty array on empty)", "{\"foo\":100}");
    testSqlApi("json_query('{\"foo\":100}', 'lax $' empty object on empty)", "{\"foo\":100}");
    testSqlApi("json_query('{\"foo\":100}', 'lax $.foo' null on empty)", "null");
    testSqlApi("json_query('{\"foo\":100}', 'lax $.foo' empty array on empty)", "[]");
    testSqlApi("json_query('{\"foo\":100}', 'lax $.foo' empty object on empty)", "{}");

    // path error test
    testSqlApi("json_query('{\"foo\":100}', 'invalid $.foo' null on error)", "null");
    testSqlApi("json_query('{\"foo\":100}', 'invalid $.foo' empty array on error)", "[]");
    testSqlApi("json_query('{\"foo\":100}', 'invalid $.foo' empty object on error)", "{}");

    // strict test
    testSqlApi("json_query('{\"foo\":100}', 'strict $' null on empty)", "{\"foo\":100}");
    testSqlApi("json_query('{\"foo\":100}', 'strict $' error on empty)", "{\"foo\":100}");
    testSqlApi("json_query('{\"foo\":100}', 'strict $' empty array on error)", "{\"foo\":100}");
    testSqlApi("json_query('{\"foo\":100}', 'strict $' empty object on error)", "{\"foo\":100}");

    testSqlApi("json_query('{\"foo\":100}', 'strict $.foo1' null on error)", "null");
    testSqlApi("json_query('{\"foo\":100}', 'strict $.foo1' empty array on error)", "[]");
    testSqlApi("json_query('{\"foo\":100}', 'strict $.foo1' empty object on error)", "{}");
    testSqlApi("json_query('{\"foo\":100}', 'strict $.foo' null on error)", "null");
    testSqlApi("json_query('{\"foo\":100}', 'strict $.foo' empty array on error)", "[]");
    testSqlApi("json_query('{\"foo\":100}', 'strict $.foo' empty object on error)", "{}");

    // array wrapper test
    testSqlApi("json_query('{\"foo\":100}', 'strict $.foo' without wrapper)", "null");
    testSqlApi("json_query('{\"foo\":100}', 'strict $.foo' without array wrapper)", "null");
    testSqlApi("json_query('{\"foo\":100}', 'strict $.foo' with wrapper)", "[100]");
    testSqlApi("json_query('{\"foo\":100}', 'strict $.foo' with unconditional wrapper)", "[100]");
    testSqlApi("json_query('{\"foo\":100}', 'strict $.foo' with conditional wrapper)", "[100]");
    testSqlApi("json_query('{\"foo\":[100]}', 'strict $.foo' without wrapper)", "[100]");
    testSqlApi("json_query('{\"foo\":[100]}', 'strict $.foo' without array wrapper)", "[100]");
    testSqlApi("json_query('{\"foo\":[100]}', 'strict $.foo' with wrapper)", "[[100]]");
    testSqlApi("json_query('{\"foo\":[100]}', 'strict $.foo' with unconditional wrapper)",
      "[[100]]");
    testSqlApi("json_query('{\"foo\":[100]}', 'strict $.foo' with conditional wrapper)", "[100]");

    // without on err test
    testSqlApi("json_query('{\"foo\":100}', " +
      "'strict $.foo' without wrapper null on error)", "null");
    testSqlApi("json_query('{\"foo\":100}', " +
      "'strict $.foo' without wrapper empty array on error)", "[]");
    testSqlApi("json_query('{\"foo\":100}', " +
      "'strict $.foo' without wrapper empty object on error)", "{}");
    testSqlApi("json_query('{\"foo\":100}', " +
      "'strict $.foo' without wrapper null on error)", "null");

    // with on err test
    testSqlApi("json_query('{\"foo\":100}', " +
      "'strict $.foo' with wrapper null on error)", "[100]");
    testSqlApi("json_query('{\"foo\":100}', " +
      "'strict $.foo' with unconditional wrapper null on error)", "[100]");

    // nulls
    testSqlApi("json_query(cast(null as varchar), 'lax $')", "null")

    testSqlApi("json_query(f8, 'lax $' null on empty)", "{\"name\":\"flink\"}")
    testSqlApi("json_query(f8, 'invalid $.name' null on error)", "null")
    testSqlApi("json_query(f8, 'strict $.name1' null on error)", "null")
    testSqlApi("json_query(f8, 'strict $' null on empty)", "{\"name\":\"flink\"}")
    testSqlApi("json_query(cast(f2 as varchar), 'lax $' null on empty)", "null")

    // invalid input and path
    testSqlApi("json_query('{]', 'invalid $.name')", "null")
    testSqlApi("json_query('{]', '$.name')", "null")
    testSqlApi("json_query(f8, 'invalid $.aa')", "null")
    testSqlApi("json_query(f8, '$.aa')", "null")
    // wrong field types
    expectedException.expect(classOf[CodeGenException])
    testSqlApi("json_query(f7, 'lax $' null on empty)", "{\"name\":\"flink\"}")
  }
}
