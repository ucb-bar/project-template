package chipyard.clocking

import chisel3._

import scala.collection.mutable.{ArrayBuffer}

import freechips.rocketchip.config.{Parameters, Field, Config}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.regmapper._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.util._
import freechips.rocketchip.tile._
import freechips.rocketchip.prci._

import testchipip.{TLTileResetCtrl}
import chipyard.{DefaultClockFrequencyKey}

case class ChipyardPRCIControlParams(
  slaveWhere: TLBusWrapperLocation = CBUS,
  baseAddress: BigInt = 0x100000,
  enableTileClockGating: Boolean = true
)


case object ChipyardPRCIControlKey extends Field[ChipyardPRCIControlParams](ChipyardPRCIControlParams())

trait HasChipyardPRCI { this: BaseSubsystem with InstantiatesTiles =>
  require(p(SubsystemDriveAsyncClockGroupsKey).isEmpty, "Subsystem asyncClockGroups must be undriven")

  implicit val n = ValName("chipyardPRCI")

  val prciParams = p(ChipyardPRCIControlKey)

  // Set up clock domain
  private val tlbus = locateTLBusWrapper(prciParams.slaveWhere)
  val prci_ctrl_domain = LazyModule(new ClockSinkDomain(name=Some("chipyard-prci-control")))
  prci_ctrl_domain.clockNode := tlbus.fixedClockNode

  // Aggregate all the clock groups into a single node
  val aggregator = LazyModule(new ClockGroupAggregator("allClocks")).node
  val allClockGroupsNode = ClockGroupEphemeralNode()

  // This must be called in the ChipTop context
  def connectImplicitClockSinkNode(sink: ClockSinkNode) =
    (sink
      := ClockGroup()
      := aggregator)

  (asyncClockGroupsNode
    :*= ClockGroupNamePrefixer()
    :*= aggregator)

  (aggregator
    := ClockGroupFrequencySpecifier(p(ClockFrequencyAssignersKey), p(DefaultClockFrequencyKey))
    := ClockGroupCombiner()
    := ClockGroupResetSynchronizer()
    := TileClockGater(prciParams.baseAddress + 0x60000, tlbus, prciParams.enableTileClockGating)
    := TileResetSetter(prciParams.baseAddress + 0x70000, tlbus, tile_prci_domains.map(_.tile_reset_domain.clockNode.portParams(0).name.get), Nil)
    := allClockGroupsNode)
}

