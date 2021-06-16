package chipyard.clocking

import chisel3._

import freechips.rocketchip.config.{Parameters, Config, Field}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.prci._

case object ClockFrequencyAssignersKey extends Field[Seq[(String) => Option[Double]]](Seq.empty)

class ClockNameMatchesAssignment(name: String, fMHz: Double) extends Config((site, here, up) => {
  case ClockFrequencyAssignersKey => up(ClockFrequencyAssignersKey, site) ++
    Seq((cName: String) => if (cName == name) Some(fMHz) else None)
})

class ClockNameContainsAssignment(name: String, fMHz: Double) extends Config((site, here, up) => {
  case ClockFrequencyAssignersKey => up(ClockFrequencyAssignersKey, site) ++
    Seq((cName: String) => if (cName.contains(name)) Some(fMHz) else None)
})


/**
  * This sort of node can be used when it is a connectivity passthrough, but modifies
  * the flow of parameters (which may result in changing the names of the underlying signals).
  */
class ClockGroupParameterModifier(
    sourceFn: ClockGroupSourceParameters => ClockGroupSourceParameters = { m => m },
    sinkFn:   ClockGroupSinkParameters   => ClockGroupSinkParameters   = { s => s })(
    implicit p: Parameters, v: ValName) extends LazyModule {
  val node = ClockGroupAdapterNode(sourceFn, sinkFn)
  lazy val module = new LazyRawModuleImp(this) {
    (node.out zip node.in).map { case ((o, _), (i, _)) =>
      (o.member.data zip i.member.data).foreach { case (oD, iD) => oD := iD }
    }
  }
}

/**
  * Pushes the ClockGroup's name into each member's name field as a prefix. This is
  * intended to be used before a ClockGroupAggregator so that sources from
  * different aggregated ClockGroups can be disambiguated by their names.
  */
object ClockGroupNamePrefixer {
  def apply()(implicit p: Parameters, valName: ValName): ClockGroupAdapterNode =
    LazyModule(new ClockGroupParameterModifier(sinkFn = { s => s.copy(members = s.members.zipWithIndex.map { case (m, idx) =>
      m.copy(name = m.name match {
          // This matches what the chisel would do if the names were not modified
          case Some(clockName) => Some(s"${s.name}_${clockName}")
          case None            => Some(s"${s.name}_${idx}")
      })
    })})).node
}

/**
  * [Word from on high is that Strings are in...]
  * Overrides the take field of all clocks in a group, by attempting to apply a
  * series of assignment functions:
  *   (name: String) => freq-in-MHz: Option[Double]
  * to each sink. Later functions that return non-empty values take priority.
  * The default if all functions return None.
  */
object ClockGroupFrequencySpecifier {
  def apply(
      assigners: Seq[(String) => Option[Double]],
      defaultFreq: Double)(
      implicit p: Parameters, valName: ValName): ClockGroupAdapterNode = {

    def lookupFrequencyForName(clock: ClockSinkParameters): ClockSinkParameters = {
      require(clock.name.nonEmpty, "All clocks in clock group must have an assigned name")
      val clockFreq = assigners.foldLeft(defaultFreq)(
        (currentFreq, candidateFunc) => candidateFunc(clock.name.get).getOrElse(currentFreq))

      clock.copy(take = clock.take match {
        case Some(cp) =>
          println(s"Clock ${clock.name.get}: using diplomatically specified frequency of ${cp.freqMHz}.")
          Some(cp)
        case None => Some(ClockParameters(clockFreq))
      })
    }

    LazyModule(new ClockGroupParameterModifier(sinkFn = { s => s.copy(members = s.members.map(lookupFrequencyForName)) })).node
  }
}
