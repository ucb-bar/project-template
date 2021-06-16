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

// Currently only works if all tiles are already driven by indepedent clock groups
// TODO: After https://github.com/chipsalliance/rocket-chip/pull/2842 is merged, we should
// always put all tiles on independent clock groups
class TileResetSetter(address: BigInt, beatBytes: Int, tileNames: Seq[String], initResetHarts: Seq[Int])(implicit p: Parameters)
    extends LazyModule {
  val device = new SimpleDevice("tile-reset-setter", Nil)
  val tlNode = TLRegisterNode(Seq(AddressSet(address, 4096-1)), device, "reg/control", beatBytes=beatBytes)
  val clockNode = ClockGroupIdentityNode()

  lazy val module = new LazyModuleImp(this) {
    val nTiles = p(TilesLocated(InSubsystem)).size
    require (nTiles <= 4096 / 4)
    val tile_async_resets = Wire(Vec(nTiles, Reset()))
    val r_tile_resets = (0 until nTiles).map({ i =>
      tile_async_resets(i) := true.B.asAsyncReset // Remove this line after https://github.com/chipsalliance/rocket-chip/pull/2842
      withReset (tile_async_resets(i)) {
        Module(new AsyncResetRegVec(w=1, init=(if (initResetHarts.contains(i)) 1 else 0)))
      }
    })
    tlNode.regmap((0 until nTiles).map({ i =>
      i * 4 -> Seq(RegField.rwReg(1, r_tile_resets(i).io)),
    }): _*)

    val tileMap = tileNames.zipWithIndex.map({ case (n, i) =>
        n -> (tile_async_resets(i), r_tile_resets(i).io.q)
    })

    (clockNode.out zip clockNode.in).map { case ((o, _), (i, _)) =>
      (o.member.elements zip i.member.elements).foreach { case ((name, oD), (_, iD)) =>
        oD.clock := iD.clock
        oD.reset := iD.reset
        for ((n, (rIn, rOut)) <- tileMap) {
          if (name.contains(n)) {
            println(name, n)
            // Async because the reset coming out of the AsyncResetRegVec is
            // clocked to the bus this is attached to, not the clock in this
            // clock bundle. We expect a ClockGroupResetSynchronizer downstream
            // to synchronize the resets
            // Also, this or enforces that the tiles come out of reset after the reset of the system
            oD.reset := (rOut.asBool || iD.reset.asBool).asAsyncReset
            rIn := iD.reset
          }
        }
      }
    }
  }
}


object TileResetSetter {
  def apply(address: BigInt, tlbus: TLBusWrapper, tileNames: Seq[String], initResetHarts: Seq[Int])(implicit p: Parameters, v: ValName) = {
    val setter = LazyModule(new TileResetSetter(address, tlbus.beatBytes, tileNames, initResetHarts))
    tlbus.toVariableWidthSlave(Some("tile-reset-setter")) { setter.tlNode := TLBuffer() }
    setter.clockNode
  }
}
