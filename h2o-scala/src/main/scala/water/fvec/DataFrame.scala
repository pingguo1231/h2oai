package water.fvec

import water._
import water.fvec._
import java.io.File

class DataFrame private ( key : Key, names : Array[String], vecs : Array[Vec] ) 
  extends Frame(key,names,vecs) 
  with Map[Long,Array[Any]] {

  // Scala DataFrame from a Frame.  Simple field copy, so the Frames share
  // underlying arrays.  Recommended that the input Java Frame be dead after
  // this call.
  def this(fr : Frame) = this(fr._key,fr._names,fr.vecs())

  // Scala DataFrame by reading a CSV file
  def this(file : File) = this(water.util.FrameUtils.parseFrame(Key.make(water.parser.ParseSetup.hex(file.getName)),file))

  // Operators for the Map and MapLike
  override def get( row : Long ) : Option[Array[Any]] = ???
  override def iterator: Iterator[(Long, Array[Any])] = ???
  override def + [B1 >: Array[Any]](kv: (Long, B1)): Map[Long,Array[Any]] = ???
  override def -(key: Long): Map[Long,Array[Any]] = ???

  override def empty : Map[Long,Array[Any]] = ???
  override def foreach[U](f: ((Long, Array[Any])) => U): Unit = ???
  override def size: Int = ???
  

//val fr = new DataFrame("airlines.csv")
//
//fr.map((Year : Int, IsDelayed : Boolean, Dist : Int) => (
//  if( Dist > 2000 && IsDelayed ) ....
//  ), (this,that) => .... )


//  def map[A : TypeTag, B, C, R](func: (A, B, C) => R) (implicit m1: TypeTag[A], m2: TypeTag[B], m3: TypeTag[C], r: TypeTag[R]) = {
//    //for all args... lookup names => indices
//    new Foo() { 
//      void map( Array[Chunk] ) {
//        val Double x = chks[0].at(row);  // specialized...
//        val Double y = chks[1].at(row);
//        val Double z = chks[2].at(row);
//        func(x,y,z)
//      }
//    }.doAll( fr.int("Year"), fr("IsDelayed"), fr("Dist"))
//  }
}


//import scala.reflect.runtime.universe._
//
//
//def map[A : TypeTag, B : TypeTag, C : TypeTag, R : TypeTag](
//  fun: (A, B, C) => R) {
//  val ttA = implicitly[TypeTag[A]]
//  val ttB = implicitly[TypeTag[B]]
//  val ttC = implicitly[TypeTag[C]]
//  val ttR = implicitly[TypeTag[R]]
//  val clzA = ttA.tpe.toString
//  val clzB = ttB.tpe.toString
//  val clzC = ttC.tpe.toString
//  val clzR = ttR.tpe.toString
//  for (dub <- List(clzA, clzB, clzC, clzR)) {
//    dub match {
//      case "Int" => println("GOT AN INT")
//      case "Integer" => println("GOT AN INTEGER")
//      case nm => println("Got: " + nm)
//    }
//  }
//}
//
//import scala.reflect.runtime.{currentMirror => m}
//
//val output = map((i: Int, fred: Boolean, horatio: Double) => if (fred) 1 else 0)
//
//// Either use a macro or...
//
//fr.map('i, 'fred, 'horatio)(i: Int, fred: Boolean, horatio: Double) => ...
//
//fr.map(mapFun).reduce(reduceFun).doit
//fr.mapReduce(mapFun, reduceFun)
//def mapReduce[A, B, C, R](mapFun: (A, B, C) => R, reduceFun: (R, R) => R): R
//fr.mapReduce(
//  (i: Int, fred: Boolean, horatio: Double) => if (fred) 1 else 0,
//  (i: Int, j: Int) => i + j)
//)
//
//
//
//val im = m reflect f
//val apply = newTermName("apply")
//val applySymbol = im.symbol.typeSignature member apply
//
