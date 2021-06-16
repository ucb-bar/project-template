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

class TileClockGater(address: BigInt, beatBytes: Int, enable: Boolean)(implicit p: Parameters, valName: ValName) extends LazyModule
{
  val device = new SimpleDevice(s"clock-gater", Nil)
  val clockNode = ClockGroupIdentityNode()
  val tlNode = TLRegisterNode(Seq(AddressSet(address, 4096-1)), device, "reg/control", beatBytes=beatBytes)
  lazy val module = new LazyModuleImp(this) {
    val sources = clockNode.in.head._1.member.data.toSeq
    val sinks = clockNode.out.head._1.member.elements.toSeq
    val nSinks = sinks.size
    val regs = (0 until nSinks).map({i =>
      val sinkName = sinks(i)._1
      val reg = withReset(sources(i).reset) { Module(new AsyncResetRegVec(w=1, init=1)) }
      if (sinkName.contains("tile") && enable) {
        println(s"ClockGate for ${sinkName} regmapped at ${(address+i*4).toString(16)}")
        sinks(i)._2.clock := ClockGate(sources(i).clock,  reg.io.q.asBool)
        sinks(i)._2.reset := sources(i).reset
      } else {
        sinks(i)._2 := sources(i)
      }
      reg
    })
    tlNode.regmap((0 until nSinks).map({i =>
      i*4 -> Seq(RegField.rwReg(1, regs(i).io))
    }): _*)
  }
}

object TileClockGater {
  def apply(address: BigInt, tlbus: TLBusWrapper, enable: Boolean)(implicit p: Parameters, v: ValName) = {
    val gater = LazyModule(new TileClockGater(address, tlbus.beatBytes, enable))
    tlbus.toVariableWidthSlave(Some("clock-gater")) { gater.tlNode := TLBuffer() }
    gater.clockNode
  }
}
