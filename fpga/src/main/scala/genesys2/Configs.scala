package chipyard.fpga.genesys2

import freechips.rocketchip.config.Config
import freechips.rocketchip.devices.debug.{JtagDTMConfig, JtagDTMKey}
import freechips.rocketchip.diplomacy.DTSTimebase
import freechips.rocketchip.subsystem.{ExtMem, PeripheryBusKey}
import sifive.blocks.devices.uart.{PeripheryUARTKey, UARTParams}
import sifive.fpgashells.shell.xilinx.Genesys2DDRSize
import testchipip.SerialTLKey

class WithDefaultPeripherals extends Config((site, here, up) => {
  case PeripheryUARTKey => List(UARTParams(address = BigInt(0x64000000L)))
})

class WithSystemModifications extends Config((site, here, up) => {
  case PeripheryBusKey => up(PeripheryBusKey, site).copy(dtsFrequency = Some(site(FPGAFrequencyKey).toInt*1000000))
  case DTSTimebase => BigInt(1000000)
  case ExtMem => up(ExtMem, site).map(x => x.copy(master = x.master.copy(size = site(Genesys2DDRSize)))) // set extmem to DDR size
  case SerialTLKey => None // remove serialized tl port
  case JtagDTMKey => JtagDTMConfig (
    idcodeVersion = 2,
    idcodePartNum = 0x000,
    idcodeManufId = 0x000,
    debugIdleCycles = 5)
})

class WithGenesys2Tweaks extends Config(
  new WithUART ++
  new WithJTAG ++
  new WithDDRMem ++
  new WithUARTIOPassthrough ++
  new WithJTAGIOPassthrough ++
  new WithTLIOPassthrough ++
  new WithDefaultPeripherals ++
  new chipyard.config.WithTLBackingMemory ++ // use TL backing memory
  new WithSystemModifications ++ // setup busses, use sdboot bootrom, setup ext. mem. size
  new freechips.rocketchip.subsystem.WithoutTLMonitors ++
  new freechips.rocketchip.subsystem.WithNMemoryChannels(1))

class RocketGenesys2Config extends Config(
  new WithFPGAFrequency(50) ++
  new WithGenesys2Tweaks ++
  new chipyard.RocketConfig)

class BoomGenesys2Config extends Config(
  new WithFPGAFrequency(50) ++
  new WithGenesys2Tweaks ++
  new chipyard.MegaBoomConfig)

class WithFPGAFrequency(MHz: Double) extends Config((site, here, up) => {
  case FPGAFrequencyKey => MHz
})

class WithFPGAFreq25MHz extends WithFPGAFrequency(25)
class WithFPGAFreq50MHz extends WithFPGAFrequency(50)
class WithFPGAFreq75MHz extends WithFPGAFrequency(75)
class WithFPGAFreq100MHz extends WithFPGAFrequency(100)
