package chipyard

import scala.reflect.ClassTag
import scala.reflect.runtime.universe._

import chisel3._

import freechips.rocketchip.config.{Parameters, Config, Field, View}
import freechips.rocketchip.subsystem.{SystemBusKey, RocketTilesKey, RocketCrossingParams, RocketCrossingKey}
import freechips.rocketchip.diplomacy.{LazyModule, ClockCrossingType, ValName}
import freechips.rocketchip.diplomaticobjectmodel.logicaltree.LogicalTreeNode
import freechips.rocketchip.rocket._
import freechips.rocketchip.tile._

import boom.common.{BoomTile, BoomTilesKey, BoomCrossingKey, BoomTileParams}
import ariane.{ArianeTile, ArianeTilesKey, ArianeCrossingKey, ArianeTileParams}

// Case class to change common parameters visible in the base traits. Some fields in the base traits may not be configurable as a 
// case class constructor parameter for some cores, and those field will be ignored when applied.
case class GenericCoreParams(
  val bootFreqHz: BigInt,
  val useVM: Boolean,
  val useUser: Boolean,
  val useSupervisor: Boolean,
  val useDebug: Boolean,
  val useAtomics: Boolean,
  val useAtomicsOnlyForIO: Boolean,
  val useCompressed: Boolean,
  override val useVector: Boolean,
  val useSCIE: Boolean,
  val useRVE: Boolean,
  val mulDiv: Option[MulDivParams],
  val fpu: Option[FPUParams],
  val fetchWidth: Int,
  val decodeWidth: Int,
  val retireWidth: Int,
  val instBits: Int,
  val nLocalInterrupts: Int,
  val nPMPs: Int,
  val pmpGranularity: Int,
  val nBreakpoints: Int,
  val useBPWatch: Boolean,
  val nPerfCounters: Int,
  val haveBasicCounters: Boolean,
  val haveFSDirty: Boolean,
  val misaWritable: Boolean,
  val haveCFlush: Boolean,
  val nL2TLBEntries: Int,
  val mtvecInit: Option[BigInt],
  val mtvecWritable: Boolean,
  // The original object
  val _origin: CoreParams
) extends CoreParams {
  def this(coreParams: CoreParams) = this(
    bootFreqHz = coreParams.bootFreqHz,
    useVM = coreParams.useVM,
    useUser = coreParams.useUser,
    useSupervisor = coreParams.useSupervisor,
    useDebug = coreParams.useDebug,
    useAtomics = coreParams.useAtomics,
    useAtomicsOnlyForIO = coreParams.useAtomicsOnlyForIO,
    useCompressed = coreParams.useCompressed,
    useVector = coreParams.useVector,
    useSCIE = coreParams.useSCIE,
    useRVE = coreParams.useRVE,
    mulDiv = coreParams.mulDiv,
    fpu = coreParams.fpu,
    fetchWidth = coreParams.fetchWidth,
    decodeWidth = coreParams.decodeWidth,
    retireWidth = coreParams.retireWidth,
    instBits = coreParams.instBits,
    nLocalInterrupts = coreParams.nLocalInterrupts,
    nPMPs = coreParams.nPMPs,
    pmpGranularity = coreParams.pmpGranularity,
    nBreakpoints = coreParams.nBreakpoints,
    useBPWatch = coreParams.useBPWatch,
    nPerfCounters = coreParams.nPerfCounters,
    haveBasicCounters = coreParams.haveBasicCounters,
    haveFSDirty = coreParams.haveFSDirty,
    misaWritable = coreParams.misaWritable,
    haveCFlush = coreParams.haveCFlush,
    nL2TLBEntries = coreParams.nL2TLBEntries,
    mtvecInit = coreParams.mtvecInit,
    mtvecWritable = coreParams.mtvecWritable,

    _origin = coreParams
  )

  // Reflection Info of this class
  val fieldNames = (this.getClass.getDeclaredFields map (f => f.getName)).init

  // Convert back to core-specific tile
  def convert: CoreParams = {
    // Reflection of target class
    val paramClass = _origin.getClass
    val paramNames = (paramClass.getDeclaredFields map (f => f.getName))
    val paramCtor = paramClass.getConstructors.head

    // Build a list of parameter in the original parameter class
    val nameDict = paramNames.zipWithIndex.toMap
    val indexList = fieldNames map (n => nameDict.get(n))
    val fieldList = this.productIterator.toList.init
    val fieldDict = ((indexList zip fieldList) collect { case (Some(i), v) => (i, v) }).toMap
    val newValues = _origin.asInstanceOf[Product].productIterator.toList.zipWithIndex map 
      { case (v, i) => (if (fieldDict contains i) fieldDict(i) else v).asInstanceOf[AnyRef] }

    paramCtor.newInstance(newValues:_*).asInstanceOf[CoreParams]
  }

  // Implement abstract function as placeholder
  def lrscCycles: Int = _origin.lrscCycles
}

case class GenericTileParams(
  val core: GenericCoreParams,
  val icache: Option[ICacheParams],
  val dcache: Option[DCacheParams],
  val btb: Option[BTBParams],
  val hartId: Int,
  val beuAddr: Option[BigInt],
  val blockerCtrlAddr: Option[BigInt],
  val name: Option[String],
  // The original object
  val _origin: TileParams
) extends TileParams {
  // Copy constructor to build the params
  def this(tileParams: TileParams) = this(
    core = new GenericCoreParams(tileParams.core),
    icache = tileParams.icache,
    dcache = tileParams.dcache,
    btb = tileParams.btb,
    hartId = tileParams.hartId,
    beuAddr = tileParams.beuAddr,
    blockerCtrlAddr = tileParams.blockerCtrlAddr,
    name = tileParams.name,

    _origin = tileParams
  )

  // Reflection Info of this class
  val fieldNames = (this.getClass.getDeclaredFields map (f => f.getName)).init

  // Convert back to core-specific tile
  def convert: TileParams = {
    // Reflection of target class
    val paramClass = _origin.getClass
    val paramNames = (paramClass.getDeclaredFields map (f => f.getName))
    val paramCtor = paramClass.getConstructors.head

    // Build a list of parameter in the original parameter class
    val nameDict = paramNames.zipWithIndex.toMap
    val indexList = fieldNames map (n => nameDict.get(n))
    val fieldList = this.productIterator.toList.updated(0, core.convert).init
    val fieldDict = ((indexList zip fieldList) collect { case (Some(i), v) => (i, v) }).toMap
    val newValues = _origin.asInstanceOf[Product].productIterator.toList.zipWithIndex map 
      { case (v, i) => (if (fieldDict contains i) fieldDict(i) else v).asInstanceOf[AnyRef] }

    paramCtor.newInstance(newValues:_*).asInstanceOf[TileParams]
  }
}

// Extractor to capture TilesKey
object TilesKey {
  def unapply(key: Any): Option[Field[Seq[_]]] =
    if ((CoreManager.cores filter (core => core.keyEqual(key))).size != 0) Some(key.asInstanceOf[Field[Seq[_]]]) else None
}

class TileSeq(list: Seq[Any]) {
  def tileMap(f: GenericTileParams => GenericTileParams): Seq[TileParams] = {
    // If this is not an unpacked tile key, simply throw a type exception
    // Static type checking is not possible when this class is used with TilesKey extractor
    val tileList = list.asInstanceOf[Seq[TileParams]]

    tileList map (tileParams => f(new GenericTileParams(tileParams)).convert)
  }
}
object TileSeq {
  implicit def convertSeq(s: Seq[Any]) = new TileSeq(s)
}