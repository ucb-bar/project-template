package chipyard.config

import scala.util.matching.Regex
import chisel3._
import chisel3.util.{log2Up}

import freechips.rocketchip.config.{Field, Parameters, Config}
import freechips.rocketchip.subsystem._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.devices.tilelink.{BootROMLocated}
import freechips.rocketchip.devices.debug.{Debug, ExportDebug, DebugModuleKey, DMI}
import freechips.rocketchip.groundtest.{GroundTestSubsystem}
import freechips.rocketchip.tile._
import freechips.rocketchip.rocket.{RocketCoreParams, MulDivParams, DCacheParams, ICacheParams}
import freechips.rocketchip.tilelink.{HasTLBusParams}
import freechips.rocketchip.util.{AsyncResetReg, Symmetric}
import freechips.rocketchip.prci._
import freechips.rocketchip.stage.phases.TargetDirKey

import testchipip._
import tracegen.{TraceGenSystem}

import hwacha.{Hwacha}
import gemmini._

import boom.common.{BoomTileAttachParams}
import cva6.{CVA6TileAttachParams}

import sifive.blocks.devices.gpio._
import sifive.blocks.devices.uart._
import sifive.blocks.devices.spi._

import chipyard._

// -----------------------
// Common Config Fragments
// -----------------------

class WithBootROM extends Config((site, here, up) => {
  case BootROMLocated(x) => up(BootROMLocated(x), site).map(_.copy(contentFileName = s"${site(TargetDirKey)}/bootrom.rv${site(XLen)}.img"))
})

// DOC include start: gpio config fragment
class WithGPIO extends Config((site, here, up) => {
  case PeripheryGPIOKey => Seq(
    GPIOParams(address = 0x10012000, width = 4, includeIOF = false))
})
// DOC include end: gpio config fragment

class WithUART(baudrate: BigInt = 115200) extends Config((site, here, up) => {
  case PeripheryUARTKey => Seq(
    UARTParams(address = 0x54000000L, nTxEntries = 256, nRxEntries = 256, initBaudRate = baudrate))
})

class WithSPIFlash(size: BigInt = 0x10000000) extends Config((site, here, up) => {
  // Note: the default size matches freedom with the addresses below
  case PeripherySPIFlashKey => Seq(
    SPIFlashParams(rAddress = 0x10040000, fAddress = 0x20000000, fSize = size))
})

class WithL2TLBs(entries: Int) extends Config((site, here, up) => {
  case TilesLocated(InSubsystem) => up(TilesLocated(InSubsystem), site) map {
    case tp: RocketTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
      core = tp.tileParams.core.copy(nL2TLBEntries = entries)))
    case tp: BoomTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
      core = tp.tileParams.core.copy(nL2TLBEntries = entries)))
    case other => other
  }
})

class WithTracegenSystem extends Config((site, here, up) => {
  case BuildSystem => (p: Parameters) => new TraceGenSystem()(p)
})

/**
 * Map from a hartId to a particular RoCC accelerator
 */
case object MultiRoCCKey extends Field[Map[Int, Seq[Parameters => LazyRoCC]]](Map.empty[Int, Seq[Parameters => LazyRoCC]])

/**
 * Config fragment to enable different RoCCs based on the hartId
 */
class WithMultiRoCC extends Config((site, here, up) => {
  case BuildRoCC => site(MultiRoCCKey).getOrElse(site(TileKey).hartId, Nil)
})

/**
 * Assigns what was previously in the BuildRoCC key to specific harts with MultiRoCCKey
 * Must be paired with WithMultiRoCC
 */
class WithMultiRoCCFromBuildRoCC(harts: Int*) extends Config((site, here, up) => {
  case BuildRoCC => Nil
  case MultiRoCCKey => up(MultiRoCCKey, site) ++ harts.distinct.map { i =>
    (i -> up(BuildRoCC, site))
  }
})

/**
 * Config fragment to add Hwachas to cores based on hart
 *
 * For ex:
 *   Core 0, 1, 2, 3 have been defined earlier
 *     with hartIds of 0, 1, 2, 3 respectively
 *   And you call WithMultiRoCCHwacha(0,1)
 *   Then Core 0 and 1 will get a Hwacha
 *
 * @param harts harts to specify which will get a Hwacha
 */
class WithMultiRoCCHwacha(harts: Int*) extends Config(
  new chipyard.config.WithHwachaTest ++
  new Config((site, here, up) => {
    case MultiRoCCKey => {
      up(MultiRoCCKey, site) ++ harts.distinct.map{ i =>
        (i -> Seq((p: Parameters) => {
          val hwacha = LazyModule(new Hwacha()(p))
          hwacha
        }))
      }
    }
  })
)

class WithMultiRoCCGemmini[T <: Data : Arithmetic, U <: Data, V <: Data](
  harts: Int*)(gemminiConfig: GemminiArrayConfig[T,U,V] = GemminiConfigs.defaultConfig) extends Config((site, here, up) => {
  case MultiRoCCKey => up(MultiRoCCKey, site) ++ harts.distinct.map { i =>
    (i -> Seq((p: Parameters) => {
      implicit val q = p
      val gemmini = LazyModule(new Gemmini(gemminiConfig))
      gemmini
    }))
  }
})

class WithTraceIO extends Config((site, here, up) => {
  case TilesLocated(InSubsystem) => up(TilesLocated(InSubsystem), site) map {
    case tp: BoomTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
      trace = true))
    case tp: CVA6TileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
      trace = true))
    case other => other
  }
  case TracePortKey => Some(TracePortParams())
})

class WithNPerfCounters(n: Int = 29) extends Config((site, here, up) => {
  case TilesLocated(InSubsystem) => up(TilesLocated(InSubsystem), site) map {
    case tp: RocketTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
      core = tp.tileParams.core.copy(nPerfCounters = n)))
    case tp: BoomTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
      core = tp.tileParams.core.copy(nPerfCounters = n)))
    case other => other
  }
})

class WithNPMPs(n: Int = 8) extends Config((site, here, up) => {
  case TilesLocated(InSubsystem) => up(TilesLocated(InSubsystem), site) map {
    case tp: RocketTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
      core = tp.tileParams.core.copy(nPMPs = n)))
    case tp: BoomTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
      core = tp.tileParams.core.copy(nPMPs = n)))
    case other => other
  }
})

class WithRocketICacheScratchpad extends Config((site, here, up) => {
  case RocketTilesKey => up(RocketTilesKey, site) map { r =>
    r.copy(icache = r.icache.map(_.copy(itimAddr = Some(0x300000 + r.hartId * 0x10000))))
  }
})

class WithRocketDCacheScratchpad extends Config((site, here, up) => {
  case RocketTilesKey => up(RocketTilesKey, site) map { r =>
    r.copy(dcache = r.dcache.map(_.copy(nSets = 32, nWays = 1, scratch = Some(0x200000 + r.hartId * 0x10000))))
  }
})

// Replaces the L2 with a broadcast manager for maintaining coherence
class WithBroadcastManager extends Config((site, here, up) => {
  case BankedL2Key => up(BankedL2Key, site).copy(coherenceManager = CoherenceManagerWrapper.broadcastManager)
})

class WithHwachaTest extends Config((site, here, up) => {
  case TestSuitesKey => (tileParams: Seq[TileParams], suiteHelper: TestSuiteHelper, p: Parameters) => {
    up(TestSuitesKey).apply(tileParams, suiteHelper, p)
    import hwacha.HwachaTestSuites._
    suiteHelper.addSuites(rv64uv.map(_("p")))
    suiteHelper.addSuites(rv64uv.map(_("vp")))
    suiteHelper.addSuite(rv64sv("p"))
    suiteHelper.addSuite(hwachaBmarks)
    "SRC_EXTENSION = $(base_dir)/hwacha/$(src_path)/*.scala" + "\nDISASM_EXTENSION = --extension=hwacha"
  }
})

// The default RocketChip BaseSubsystem drives its diplomatic clock graph
// with the implicit clocks of Subsystem. Don't do that, instead we extend
// the diplomacy graph upwards into the ChipTop, where we connect it to
// our clock drivers
class WithNoSubsystemDrivenClocks extends Config((site, here, up) => {
  case SubsystemDriveAsyncClockGroupsKey => None
})

class WithDMIDTM extends Config((site, here, up) => {
  case ExportDebug => up(ExportDebug, site).copy(protocols = Set(DMI))
})

class WithNoDebug extends Config((site, here, up) => {
  case DebugModuleKey => None
})

class WithTLSerialLocation(masterWhere: TLBusWrapperLocation, slaveWhere: TLBusWrapperLocation) extends Config((site, here, up) => {
  case SerialTLAttachKey => up(SerialTLAttachKey, site).copy(masterWhere = masterWhere, slaveWhere = slaveWhere)
})

class WithTLBackingMemory extends Config((site, here, up) => {
  case ExtMem => None // disable AXI backing memory
  case ExtTLMem => up(ExtMem, site) // enable TL backing memory
})

class WithSerialTLBackingMemory extends Config((site, here, up) => {
  case ExtMem => None
  case SerialTLKey => up(SerialTLKey, site).map { k => k.copy(
    memParams = {
      val memPortParams = up(ExtMem, site).get
      require(memPortParams.nMemoryChannels == 1)
      memPortParams.master
    },
    isMemoryDevice = true
  )}
})

/**
  * Mixins to define either a specific tile frequency for a single hart or all harts
  *
  * @param fMHz Frequency in MHz of the tile or all tiles
  * @param hartId Optional hartid to assign the frequency to (if unspecified default to all harts)
  */
class WithTileFrequency(fMHz: Double, hartId: Option[Int] = None) extends ClockNameContainsAssignment({
    hartId match {
      case Some(id) => s"tile_$id"
      case None => "tile"
    }
  },
  fMHz)

class WithPeripheryBusFrequencyAsDefault extends Config((site, here, up) => {
  case DefaultClockFrequencyKey => (site(PeripheryBusKey).dtsFrequency.get / (1000 * 1000)).toDouble
})

class WithSystemBusFrequencyAsDefault extends Config((site, here, up) => {
  case DefaultClockFrequencyKey => (site(SystemBusKey).dtsFrequency.get / (1000 * 1000)).toDouble
})

class BusFrequencyAssignment[T <: HasTLBusParams](re: Regex, key: Field[T]) extends Config((site, here, up) => {
  case ClockFrequencyAssignersKey => up(ClockFrequencyAssignersKey, site) ++
    Seq((cName: String) => site(key).dtsFrequency.flatMap { f =>
      re.findFirstIn(cName).map {_ => (f / (1000 * 1000)).toDouble }
    })
})

/**
  * Provides a diplomatic frequency for all clock sinks with an unspecified
  * frequency bound to each bus.
  *
  * For example, the L2 cache, when bound to the sbus, receives a separate
  * clock that appears as "subsystem_sbus_<num>".  This fragment ensures that
  * clock requests the same frequency as the sbus itself.
  */

class WithInheritBusFrequencyAssignments extends Config(
  new BusFrequencyAssignment("subsystem_sbus_\\d+".r, SystemBusKey) ++
  new BusFrequencyAssignment("subsystem_pbus_\\d+".r, PeripheryBusKey) ++
  new BusFrequencyAssignment("subsystem_cbus_\\d+".r, ControlBusKey) ++
  new BusFrequencyAssignment("subsystem_fbus_\\d+".r, FrontBusKey) ++
  new BusFrequencyAssignment("subsystem_mbus_\\d+".r, MemoryBusKey)
)

/**
  * Mixins to specify crossing types between the 5 traditional TL buses
  *
  * Note: these presuppose the legacy connections between buses and set
  * parameters in SubsystemCrossingParams; they may not be resuable in custom
  * topologies (but you can specify the desired crossings in your topology).
  *
  * @param xType The clock crossing type
  *
  */

class WithSbusToMbusCrossingType(xType: ClockCrossingType) extends Config((site, here, up) => {
    case SbusToMbusXTypeKey => xType
})
class WithSbusToCbusCrossingType(xType: ClockCrossingType) extends Config((site, here, up) => {
    case SbusToCbusXTypeKey => xType
})
class WithCbusToPbusCrossingType(xType: ClockCrossingType) extends Config((site, here, up) => {
    case CbusToPbusXTypeKey => xType
})
class WithFbusToSbusCrossingType(xType: ClockCrossingType) extends Config((site, here, up) => {
    case FbusToSbusXTypeKey => xType
})

/**
  * Mixins to set the dtsFrequency field of BusParams -- these will percolate its way
  * up the diplomatic graph to the clock sources.
  */
class WithPeripheryBusFrequency(freqMHz: Double) extends Config((site, here, up) => {
  case PeripheryBusKey => up(PeripheryBusKey, site).copy(dtsFrequency = Some(BigInt((freqMHz * 1e6).toLong)))
})
class WithMemoryBusFrequency(freqMHz: Double) extends Config((site, here, up) => {
  case MemoryBusKey => up(MemoryBusKey, site).copy(dtsFrequency = Some(BigInt((freqMHz * 1e6).toLong)))
})
class WithSystemBusFrequency(freqMHz: Double) extends Config((site, here, up) => {
  case SystemBusKey => up(SystemBusKey, site).copy(dtsFrequency = Some(BigInt((freqMHz * 1e6).toLong)))
})
class WithFrontBusFrequency(freqMHz: Double) extends Config((site, here, up) => {
  case FrontBusKey => up(FrontBusKey, site).copy(dtsFrequency = Some(BigInt((freqMHz * 1e6).toLong)))
})
class WithControlBusFrequency(freqMHz: Double) extends Config((site, here, up) => {
  case ControlBusKey => up(ControlBusKey, site).copy(dtsFrequency = Some(BigInt((freqMHz * 1e6).toLong)))
})

class WithRationalMemoryBusCrossing extends WithSbusToMbusCrossingType(RationalCrossing(Symmetric))
class WithAsynchrousMemoryBusCrossing extends WithSbusToMbusCrossingType(AsynchronousCrossing())
