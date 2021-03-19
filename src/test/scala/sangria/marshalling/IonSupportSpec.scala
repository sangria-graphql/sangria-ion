package sangria.marshalling

import java.nio.charset.Charset
import java.util.{Calendar, TimeZone}

import sangria.marshalling.testkit._
import software.amazon.ion.Timestamp
import software.amazon.ion.system.IonSystemBuilder
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class IonSupportSpec
    extends AnyWordSpec
    with Matchers
    with MarshallingBehaviour
    with InputHandlingBehaviour
    with ParsingBehaviour {
  import sangria.marshalling.ion._

  implicit val ionSystem = IonSystemBuilder.standard().build()

  "Ion integration" should {
    behave.like(`value (un)marshaller`(ionResultMarshaller))

    behave.like(`AST-based input unmarshaller`(ionFromInput))
    behave.like(`AST-based input marshaller`(ionResultMarshaller))

    behave.like(
      `input parser`(ParseTestSubjects(
        complex = """{a: [null, 123, [{foo: "bar"}]], b: {c: true, d: null}}""",
        simpleString = "\"bar\"",
        simpleInt = "12345",
        simpleNull = "null",
        list = "[\"bar\", 1, null, true, [1, 2, 3]]",
        syntaxError = List("[123, FOO BAR")
      )))
  }

  val toRender =
    ionSystem.getLoader
      .load("""
      {a:[null,123,[
        {foo:"bar"}]],
        b:{c:true,d:null}}
    """)
      .get(0)

  "InputUnmarshaller" should {
    "throw an exception on invalid scalar values" in {
      an[IllegalStateException] should be thrownBy
        ionInputUnmarshaller.getScalarValue(ionSystem.newEmptyStruct())
    }

    "throw an exception on variable names" in {
      an[IllegalArgumentException] should be thrownBy
        ionInputUnmarshaller.getVariableName(ionSystem.newString("foo"))
    }

    "render JSON values" in {
      val rendered = ionInputUnmarshaller.render(toRender)

      rendered should be("""{a:[null,123,[{foo:"bar"}]],b:{c:true,d:null}}""")
    }
  }

  "ResultMarshaller" should {
    "render pretty JSON values" in {
      val rendered = ionResultMarshaller.renderPretty(toRender)

      rendered.replaceAll("\r", "") should be("""
          |{
          |  a:[
          |    null,
          |    123,
          |    [
          |      {
          |        foo:"bar"
          |      }
          |    ]
          |  ],
          |  b:{
          |    c:true,
          |    d:null
          |  }
          |}""".stripMargin.replaceAll("\r", ""))
    }

    "render compact JSON values" in {
      val rendered = ionResultMarshaller.renderCompact(toRender)

      rendered should be("""{a:[null,123,[{foo:"bar"}]],b:{c:true,d:null}}""")
    }

    val rm = ionResultMarshaller
    val iu = ionInputUnmarshaller

    val calendar = {
      val cal = Calendar.getInstance(TimeZone.getTimeZone("CET"))
      cal.set(2015, 5, 11, 18, 23, 14)
      cal.set(Calendar.MILLISECOND, 123)
      cal
    }

    val bytes = "foo bar".getBytes("UTF-8")

    "marshal `Long` scalar values" in {
      val marshaled = rm.scalarNode(123434252243534L, "Test", Set.empty)

      marshaled should be(ionSystem.newInt(123434252243534L))
    }

    "marshal `java.util.Date` scalar values" in {
      val marshaled = rm.scalarNode(calendar.getTime, "Test", Set.empty)

      marshaled should be(ionSystem.newUtcTimestamp(calendar.getTime))
    }

    "marshal `java.util.Calendar` scalar values" in {
      val marshaled = rm.scalarNode(calendar, "Test", Set.empty)

      marshaled should be(ionSystem.newTimestamp(Timestamp.forCalendar(calendar)))
    }

    "marshal `Timestamp` scalar values" in {
      val marshaled = rm.scalarNode(Timestamp.forCalendar(calendar), "Test", Set.empty)

      marshaled should be(ionSystem.newTimestamp(Timestamp.forCalendar(calendar)))
    }

    "marshal `java.sql.Timestamp` scalar values" in {
      val st = new java.sql.Timestamp(calendar.getTime.getTime)
      val marshaled = rm.scalarNode(st, "Test", Set.empty)

      marshaled should be(ionSystem.newTimestamp(Timestamp.forSqlTimestampZ(st)))
    }

    "marshal blob scalar values" in {
      val marshaled = rm.scalarNode(bytes, "Test", Set.empty)

      marshaled should be(ionSystem.newBlob(bytes))
    }

    "marshal clob scalar values" in {
      val marshaled = rm.scalarNode(bytes, "Test", Set(IonClobScalar))

      marshaled should be(ionSystem.newClob(bytes))
    }

    "marshal string clob scalar values" in {
      val marshaled =
        rm.scalarNode("abcdÖ", "Test", Set(IonClobStringScalar(Charset.forName("UTF-8"))))

      marshaled should be(ionSystem.newClob("abcdÖ".getBytes("UTF-8")))
    }

    "result in error for unsupported scalar values" in {
      an[IllegalArgumentException] should be thrownBy rm.scalarNode(
        IonClobScalar,
        "Test",
        Set.empty)
    }
  }
}
