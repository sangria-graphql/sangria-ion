package sangria.marshalling

import java.nio.charset.Charset
import java.util.{Calendar, Date}

import software.amazon.ion.system.IonTextWriterBuilder
import software.amazon.ion._

import scala.collection.JavaConverters._
import scala.util.Try

object ion {
  sealed trait IonScalarInfo extends ScalarValueInfo

  /**
    * Instructs marshaller to encode `Array[Byte]` and `String` as `clob` ion type
    */
  case object IonClobScalar extends IonScalarInfo

  /**
    * Instructs marshaller to encode `String` as `clob` ion type with provided charset
    */
  case class IonClobStringScalar(charset: Charset) extends IonScalarInfo

  sealed trait IonCapability extends MarshallerCapability

  case object IonTimestampSupport extends IonCapability
  case object IonSqlTimestampSupport extends IonCapability
  case object IonClobSupport extends IonCapability
  case object IonClobStringSupport extends IonCapability

  class IonResultMarshaller(system: IonSystem) extends ResultMarshaller {
    type Node = IonValue
    type MapBuilder = IonStruct

    def emptyMapNode(keys: Seq[String]) = system.newEmptyStruct()

    def addMapNodeElem(builder: MapBuilder, key: String, value: IonValue, optional: Boolean) = {
      builder.put(key, value)
      builder
    }

    def mapNode(builder: MapBuilder) = builder

    def mapNode(keyValues: Seq[(String, IonValue)]) = {
      val struct = system.newEmptyStruct()

      keyValues.foreach {
        case (k, v) => struct.put(k, v)
      }

      struct
    }

    def arrayNode(values: Vector[IonValue]) = system.newList(values: _*)
    def optionalArrayNodeValue(value: Option[IonValue]) = value match {
      case Some(v) => v
      case None => nullNode
    }

    def scalarNode(value: Any, typeName: String, info: Set[ScalarValueInfo]) = value match {
      case v: String if info.contains(IonClobScalar) => system.newClob(v.getBytes())
      case v: String if info.exists(_.isInstanceOf[IonClobStringScalar]) =>
        system.newClob(v.getBytes(info.collectFirst{case IonClobStringScalar(charset) => charset}.get))
      case v: String => system.newString(v)
      case v: Boolean => system.newBool(v)
      case v: Int => system.newInt(v)
      case v: Long => system.newInt(v)
      case v: Float => system.newFloat(v)
      case v: Double => system.newFloat(v)
      case v: BigInt => system.newInt(v.bigInteger)
      case v: BigDecimal => system.newDecimal(v.bigDecimal)
      case v: java.sql.Timestamp => system.newTimestamp(Timestamp.forSqlTimestampZ(v))
      case v: Date => system.newUtcTimestamp(v)
      case v: Calendar => system.newTimestamp(Timestamp.forCalendar(v))
      case v: Timestamp => system.newTimestamp(v)
      case v: Array[Byte] if info.contains(IonClobScalar) => system.newClob(v)
      case v: Array[Byte] => system.newBlob(v)
      case v => throw new IllegalArgumentException("Unsupported scalar value: " + v)
    }

    def enumNode(value: String, typeName: String) = system.newString(value)

    def nullNode = system.newNull()

    def renderPretty(node: IonValue) = renderPrettyValue(system, node)
    def renderCompact(node: IonValue) = renderCompactValue(system, node)

    override def capabilities = Set(
      IonTimestampSupport,
      IonSqlTimestampSupport,
      DateSupport,
      CalendarSupport,
      BlobSupport,
      IonClobSupport,
      IonClobStringSupport)
  }

  implicit def ionResultMarshaller(implicit system: IonSystem) =
    new IonResultMarshaller(system)

  class IonMarshallerForType(system: IonSystem) extends ResultMarshallerForType[IonValue] {
    val marshaller = new IonResultMarshaller(system)
  }

  implicit def ionMarshallerForType(implicit system: IonSystem) =
    new IonMarshallerForType(system)

  class IonInputUnmarshaller(system: IonSystem) extends InputUnmarshaller[IonValue] {
    def getRootMapValue(node: IonValue, key: String) = Option(node.asInstanceOf[IonStruct].get(key))

    def isMapNode(node: IonValue) = node.isInstanceOf[IonStruct]
    def getMapValue(node: IonValue, key: String) = Option(node.asInstanceOf[IonStruct].get(key))

    // preserve order
    def getMapKeys(node: IonValue) = node.asInstanceOf[IonStruct].iterator().asScala.map(_.getFieldName).toVector

    def isListNode(node: IonValue) = node.isInstanceOf[IonList]
    def getListValue(node: IonValue) = node.asInstanceOf[IonList].asScala.toSeq

    def isDefined(node: IonValue) = !node.isNullValue
    def getScalarValue(node: IonValue) = node match {
      case v: IonBool => v.booleanValue
      case v: IonText => v.stringValue
      case v: IonFloat => v.doubleValue
      case v: IonInt => BigInt(v.bigIntegerValue)
      case v: IonDecimal => BigDecimal(v.bigDecimalValue)
      case v: IonClob => v.stringValue(Charset.forName("UTF-8"))
      case v => throw new IllegalStateException(s"'$v' is not a supported scalar value")
    }

    def getScalaScalarValue(node: IonValue) = getScalarValue(node)


    def isEnumNode(node: IonValue) = node.isInstanceOf[IonText] || node.isInstanceOf[IonClob]
    def isScalarNode(node: IonValue) = node match {
      case _: IonBool | _: IonText | _: IonFloat | _ : IonInt | _: IonDecimal | _: IonClob => true
      case _ => false
    }

    def isVariableNode(node: IonValue) = false
    def getVariableName(node: IonValue) = throw new IllegalArgumentException("variables are not supported")

    def render(node: IonValue) = renderCompactValue(system, node)
  }

  implicit def ionInputUnmarshaller(implicit system: IonSystem) =
    new IonInputUnmarshaller(system)


  class IonToInput(system: IonSystem) extends ToInput[IonValue, IonValue] {
    def toInput(value: IonValue) = (value, ionInputUnmarshaller(system))
  }

  implicit def ionToInput(implicit system: IonSystem) =
    new IonToInput(system)

  class IonFromInput(system: IonSystem) extends FromInput[IonValue] {
    val marshaller = ionResultMarshaller(system)
    def fromResult(node: marshaller.Node) = node
  }

  implicit def ionFromInput(implicit system: IonSystem) =
    new IonFromInput(system)

  private def renderPrettyValue(system: IonSystem, value: IonValue) = {
    val buf = new StringBuffer
    val writer = IonTextWriterBuilder.pretty().build(buf)

    try {
      writer.writeValues(system.newReader(value))
      writer.flush()

      buf.toString
    } finally writer.close()
  }

  private def renderCompactValue(system: IonSystem, value: IonValue) = {
    val buf = new StringBuffer
    val writer = IonTextWriterBuilder.standard().build(buf)

    try {
      writer.writeValues(system.newReader(value))
      writer.flush()

      buf.toString
    } finally writer.close()
  }

  class IonInputParser(system: IonSystem) extends InputParser[IonValue] {
    def parse(str: String) = Try(system.getLoader.load(str).get(0))
  }

  implicit def ionInputParser(implicit system: IonSystem) =
    new IonInputParser(system)


}
