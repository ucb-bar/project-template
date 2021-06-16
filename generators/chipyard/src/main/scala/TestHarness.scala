package chipyard

import chisel3._

import scala.collection.mutable.{ArrayBuffer, LinkedHashMap}
import freechips.rocketchip.diplomacy.{LazyModule}
import freechips.rocketchip.config.{Field, Parameters}
import freechips.rocketchip.util.{ResetCatchAndSync}
import freechips.rocketchip.prci.{ClockBundle, ClockBundleParameters, ClockSinkParameters, ClockParameters}

import chipyard.harness.{ApplyHarnessBinders, HarnessBinders}
import chipyard.iobinders.HasIOBinders
import chipyard.clocking.{SimplePllConfiguration, ClockDividerN}

// -------------------------------
// Chipyard Test Harness
// -------------------------------

case object BuildTop extends Field[Parameters => LazyModule]((p: Parameters) => new ChipTop()(p))
case object DefaultClockFrequencyKey extends Field[Double](100.0)

trait HasHarnessSignalReferences {
  implicit val p: Parameters
  // clock/reset of the chiptop reference clock (can be different than the implicit harness clock/reset)
  var refClockFreq: Double = p(DefaultClockFrequencyKey)
  def setRefClockFreq(freqMHz: Double) = { refClockFreq = freqMHz }
  def getRefClockFreq: Double = refClockFreq
  def buildtopClock: Clock
  def buildtopReset: Reset
  def dutReset: Reset
  def success: Bool
}

class HarnessClockInstantiator {
  private val _clockMap: LinkedHashMap[String, (Double, ClockBundle)] = LinkedHashMap.empty

  // request a clock bundle at a particular frequency
  def requestClockBundle(name: String, freqRequested: Double): ClockBundle = {
    val clockBundle = Wire(new ClockBundle(ClockBundleParameters()))
    _clockMap(name) = (freqRequested, clockBundle)
    clockBundle
  }

  // connect all clock wires specified to a divider only PLL
  def instantiateHarnessDividerPLL(refClock: ClockBundle): Unit = {
    val sinks = _clockMap.map({ case (name, (freq, bundle)) =>
      ClockSinkParameters(take=Some(ClockParameters(freqMHz=freq / (1000 * 1000))), name=Some(name))
    }).toSeq

    val pllConfig = new SimplePllConfiguration("harnessDividerOnlyClockGenerator", sinks)
    pllConfig.emitSummaries()

    val dividedClocks = LinkedHashMap[Int, Clock]()
    def instantiateDivider(div: Int): Clock = {
      val divider = Module(new ClockDividerN(div))
      divider.suggestName(s"ClockDivideBy${div}")
      divider.io.clk_in := refClock.clock
      dividedClocks(div) = divider.io.clk_out
      divider.io.clk_out
    }

    // connect wires to clock source
    for (sinkParams <- sinks) {
      // bypass the reference freq. (don't create a divider + reset sync)
      val (divClock, divReset) = if (sinkParams.take.get.freqMHz != pllConfig.referenceFreqMHz) {
        val div = pllConfig.sinkDividerMap(sinkParams)
        val divClock = dividedClocks.getOrElse(div, instantiateDivider(div))
        (divClock, ResetCatchAndSync(divClock, refClock.reset.asBool))
      } else {
        (refClock.clock, refClock.reset)
      }

      _clockMap(sinkParams.name.get)._2.clock := divClock
      _clockMap(sinkParams.name.get)._2.reset := divReset
    }
  }
}

case object HarnessClockInstantiatorKey extends Field[HarnessClockInstantiator](new HarnessClockInstantiator)

class TestHarness(implicit val p: Parameters) extends Module with HasHarnessSignalReferences {
  val io = IO(new Bundle {
    val success = Output(Bool())
  })

  val buildtopClock = Wire(Clock())
  val buildtopReset = Wire(Reset())

  val lazyDut = LazyModule(p(BuildTop)(p)).suggestName("chiptop")
  val dut = Module(lazyDut.module)

  io.success := false.B

  val dutReset = buildtopReset.asAsyncReset
  val success = io.success

  lazyDut match { case d: HasIOBinders =>
    ApplyHarnessBinders(this, d.lazySystem, d.portMap)
  }

  val refClkBundle = p(HarnessClockInstantiatorKey).requestClockBundle("buildtop_reference_clock", getRefClockFreq * (1000 * 1000))

  buildtopClock := refClkBundle.clock
  buildtopReset := WireInit(refClkBundle.reset)


  val implicitHarnessClockBundle = Wire(new ClockBundle(ClockBundleParameters()))
  implicitHarnessClockBundle.clock := clock
  implicitHarnessClockBundle.reset := reset
  p(HarnessClockInstantiatorKey).instantiateHarnessDividerPLL(implicitHarnessClockBundle)
}

