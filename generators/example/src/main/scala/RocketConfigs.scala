package example

import chisel3._

import freechips.rocketchip.config.{Config}

// --------------
// Rocket Configs
// --------------

class RocketConfig extends Config(
  new WithTop ++                                           // use default top
  new WithBootROM ++                                       // use default bootrom
  new freechips.rocketchip.subsystem.WithInclusiveCache ++ // use Sifive L2 cache
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++   // single rocket-core
  new freechips.rocketchip.system.BaseConfig)              // "base" rocketchip system

class TutorialRocketConfig extends Config(
  new WithTop ++                                           // use default top
  new WithBootROM ++                                       // use default bootrom
  new freechips.rocketchip.subsystem.WithInclusiveCache ++ // use Sifive L2 cache
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++   // single rocket-core
  new freechips.rocketchip.system.BaseConfig)              // "base" rocketchip system

class HwachaRocketConfig extends Config(
  new WithTop ++
  new WithBootROM ++
  new freechips.rocketchip.subsystem.WithInclusiveCache ++
  new hwacha.DefaultHwachaConfig ++                        // use Hwacha vector accelerator
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new freechips.rocketchip.system.BaseConfig)

class RoccRocketConfig extends Config(
  new WithTop ++
  new WithBootROM ++
  new freechips.rocketchip.subsystem.WithInclusiveCache ++
  new freechips.rocketchip.subsystem.WithRoccExample ++    // use example RoCC-based accelerator
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new freechips.rocketchip.system.BaseConfig)

// DOC include start: JtagRocket
class jtagRocketConfig extends Config(
  new WithDTMTop ++                                        // use top with dtm
  new freechips.rocketchip.subsystem.WithJtagDTM ++        // add jtag+DTM module to coreplex
  new WithBootROM ++
  new freechips.rocketchip.subsystem.WithInclusiveCache ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new freechips.rocketchip.system.BaseConfig)
// DOC include end: JtagRocket

// DOC include start: DmiRocket
class dmiRocketConfig extends Config(
  new WithDTMTop ++                                        // use top with dtm
  new WithBootROM ++
  new freechips.rocketchip.subsystem.WithInclusiveCache ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new freechips.rocketchip.system.BaseConfig)
// DOC include end: DmiRocket

// DOC include start: PWMRocketConfig
class PWMRocketConfig extends Config(
  new WithPWMTop ++                                        // use top with tilelink-controlled PWM
  new WithBootROM ++
  new freechips.rocketchip.subsystem.WithInclusiveCache ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new freechips.rocketchip.system.BaseConfig)
// DOC include end: PWMRocketConfig

class PWMAXI4RocketConfig extends Config(
  new WithPWMAXI4Top ++                                    // use top with axi4-controlled PWM
  new WithBootROM ++
  new freechips.rocketchip.subsystem.WithInclusiveCache ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new freechips.rocketchip.system.BaseConfig)

class GCDRocketConfig extends Config(                      // add MMIO GCD module
  new WithGCDTop ++
  new WithBootROM ++
  new freechips.rocketchip.subsystem.WithInclusiveCache ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new freechips.rocketchip.system.BaseConfig)

class SimBlockDeviceRocketConfig extends Config(
  new testchipip.WithBlockDevice ++                        // add block-device module to peripherybus
  new WithSimBlockDeviceTop ++                             // use top with block-device IOs and connect to simblockdevice
  new WithBootROM ++
  new freechips.rocketchip.subsystem.WithInclusiveCache ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new freechips.rocketchip.system.BaseConfig)

class BlockDeviceModelRocketConfig extends Config(
  new testchipip.WithBlockDevice ++                        // add block-device module to periphery bus
  new WithBlockDeviceModelTop ++                           // use top with block-device IOs and connect to a blockdevicemodel
  new WithBootROM ++
  new freechips.rocketchip.subsystem.WithInclusiveCache ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new freechips.rocketchip.system.BaseConfig)

// DOC include start: GPIORocketConfig
class GPIORocketConfig extends Config(
  new WithGPIO ++                                          // add GPIOs to the peripherybus
  new WithGPIOTop ++                                       // use top with GPIOs
  new WithBootROM ++
  new freechips.rocketchip.subsystem.WithInclusiveCache ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new freechips.rocketchip.system.BaseConfig)
// DOC include end: GPIORocketConfig

class DualCoreRocketConfig extends Config(
  new WithTop ++
  new WithBootROM ++
  new freechips.rocketchip.subsystem.WithInclusiveCache ++
  new freechips.rocketchip.subsystem.WithNBigCores(2) ++   // dual-core (2 RocketTiles)
  new freechips.rocketchip.system.BaseConfig)

class RV32RocketConfig extends Config(
  new WithTop ++
  new WithBootROM ++
  new freechips.rocketchip.subsystem.WithInclusiveCache ++
  new freechips.rocketchip.subsystem.WithRV32 ++           // set RocketTiles to be 32-bit
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new freechips.rocketchip.system.BaseConfig)

class GB1MemoryRocketConfig extends Config(
  new WithTop ++
  new WithBootROM ++
  new freechips.rocketchip.subsystem.WithInclusiveCache ++
  new freechips.rocketchip.subsystem.WithExtMemSize((1<<30) * 1L) ++ // use 2GB simulated external memory
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new freechips.rocketchip.system.BaseConfig)

// DOC include start: Sha3Rocket
// DOC include end: Sha3Rocket

// DOC include start: InitZeroRocketConfig
class InitZeroRocketConfig extends Config(
  new WithInitZero(0x88000000L, 0x1000L) ++
  new WithInitZeroTop ++
  new WithBootROM ++
  new freechips.rocketchip.subsystem.WithInclusiveCache ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new freechips.rocketchip.system.BaseConfig)
// DOC include end: InitZeroRocketConfig
