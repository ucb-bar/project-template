package chipyard.clocking

import chisel3._
import chisel3.util._
import chisel3.experimental.{Analog, IO}

import freechips.rocketchip.config._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.prci._
import freechips.rocketchip.util._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.regmapper._
import freechips.rocketchip.subsystem._

object ClockGroupCombiner {
  def apply()(implicit p: Parameters, valName: ValName): ClockGroupAdapterNode = {
    LazyModule(new ClockGroupCombiner()).node
  }
}

case object ClockGroupCombinerKey extends Field[Seq[(String, ClockSinkParameters => Boolean)]](Nil)

// All clock groups with a name containing any substring in names will be combined into a single clock group
class WithClockGroupsCombinedByName(grouped_name: String, names: String*) extends Config((site, here, up) => {
  case ClockGroupCombinerKey => {
    val combiner: ClockSinkParameters => Boolean = { m => names.map(n => m.name.get.contains(n)).reduce(_||_) }
    up(ClockGroupCombinerKey) ++ Seq((grouped_name, combiner))
  }
})

class ClockGroupCombiner(implicit p: Parameters, v: ValName) extends LazyModule {
  val combiners = p(ClockGroupCombinerKey)
  val sourceFn: ClockGroupSourceParameters => ClockGroupSourceParameters = { m => m }
  val sinkFn: ClockGroupSinkParameters   => ClockGroupSinkParameters = { u =>
    var i = 0
    val (grouped, rest) = combiners.map(_._2).foldLeft((Seq[ClockSinkParameters](), u.members)) { case ((grouped, rest), c) =>
      val (g, r) = rest.partition(c(_))
      val name = combiners(i)._1
      i = i + 1
      require(g.size >= 1)
      require(g.forall(_.take.get == g.head.take.get))
      (grouped ++ Seq(ClockSinkParameters(take = g.head.take, name = Some(name))), r)
    }
    ClockGroupSinkParameters(
      name = u.name,
      members = grouped ++ rest
    )
  }


  val node = ClockGroupAdapterNode(sourceFn, sinkFn)
  lazy val module = new LazyRawModuleImp(this) {
    (node.out zip node.in).map { case ((o, oe), (i, ie)) =>
      {
        val inMap = (i.member.data zip ie.sink.members).map { case (id, im) =>
          im.name.get -> id
        }.toMap
        (o.member.data zip oe.sink.members).map { case (od, om) =>
          val matches = combiners.filter(c => c._2(om))
          require(matches.size <= 1)
          if (matches.size == 0) {
            od := inMap(om.name.get)
          } else {
            od := inMap(matches(0)._1)
          }
        }
      }
    }
  }
}
