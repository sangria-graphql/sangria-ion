package sangria.marshalling

import org.scalatest.{Matchers, WordSpec}

import sangria.marshalling.testkit._
import software.amazon.ion.system.IonSystemBuilder

class IonSupportSpec extends WordSpec with Matchers with MarshallingBehaviour with InputHandlingBehaviour with ParsingBehaviour {
  import sangria.marshalling.ion._

  implicit val ionSystem = IonSystemBuilder.standard().build()

  "Ion integration" should {
    behave like `value (un)marshaller` (ionResultMarshaller)

    behave like `AST-based input unmarshaller` (ionFromInput)
    behave like `AST-based input marshaller` (ionResultMarshaller)

    behave like `input parser` (ParseTestSubjects(
      complex = """{a: [null, 123, [{foo: "bar"}]], b: {c: true, d: null}}""",
      simpleString = "\"bar\"",
      simpleInt = "12345",
      simpleNull = "null",
      list = "[\"bar\", 1, null, true, [1, 2, 3]]",
      syntaxError = List("[123, FOO BAR")
    ))
  }

  val toRender =
    ionSystem.getLoader.load("""
      {a:[null,123,[
        {foo:"bar"}]],
        b:{c:true,d:null}}
    """).get(0)

  "InputUnmarshaller" should {
    "throw an exception on invalid scalar values" in {
      an [IllegalStateException] should be thrownBy
        ionInputUnmarshaller.getScalarValue(ionSystem.newEmptyStruct())
    }

    "throw an exception on variable names" in {
      an [IllegalArgumentException] should be thrownBy
          ionInputUnmarshaller.getVariableName(ionSystem.newString("foo"))
    }

    "render JSON values" in {
      val rendered = ionInputUnmarshaller.render(toRender)

      rendered should be ("""{a:[null,123,[{foo:"bar"}]],b:{c:true,d:null}}""")
    }
  }

  "ResultMarshaller" should {
    "render pretty JSON values" in {
      val rendered = ionResultMarshaller.renderPretty(toRender)

      rendered.replaceAll("\r", "") should be (
        """
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

      rendered should be ("""{a:[null,123,[{foo:"bar"}]],b:{c:true,d:null}}""")
    }
  }
}
