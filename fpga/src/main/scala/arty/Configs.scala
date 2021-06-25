// See LICENSE for license details.
package chipyard.fpga.arty

import sys.process._

import freechips.rocketchip.config._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.devices.debug._
import freechips.rocketchip.devices.tilelink.{BootROMLocated}
import freechips.rocketchip.diplomacy.{DTSModel, DTSTimebase}
import freechips.rocketchip.system._
import freechips.rocketchip.tile._

import sifive.blocks.devices.gpio._
import sifive.blocks.devices.uart._
import sifive.blocks.devices.spi._

import testchipip.{SerialTLKey}

import chipyard.{BuildSystem}

class WithDefaultPeripherals extends Config((site, here, up) => {
  case PeripheryUARTKey => List(
    UARTParams(address = 0x10013000))
  case DTSTimebase => BigInt(32768)
  case JtagDTMKey => new JtagDTMConfig (
    idcodeVersion = 2,
    idcodePartNum = 0x000,
    idcodeManufId = 0x489,
    debugIdleCycles = 5)
  case SerialTLKey => None // remove serialized tl port
  case PeripherySPIFlashKey => List(
    SPIFlashParams(
      fAddress = 0x20000000,
      rAddress = 0x10014000,
      defaultSampleDel = 3))
  case BootROMLocated(x) => up(BootROMLocated(x), site).map { p =>
    // invoke makefile for xip
    val make = s"make -C fpga/src/main/resources/arty/xip bin"
    require (make.! == 0, "Failed to build bootrom")
    p.copy(hang = 0x10000, contentFileName = s"./fpga/src/main/resources/arty/xip/build/xip.bin")
  }
})

// DOC include start: AbstractArty and Rocket
class WithArtyTweaks extends Config(
  new WithArtyGPIOHarnessBinder ++
  new WithGPIOPassthrough ++
  new WithArtyJTAGHarnessBinder ++
  new WithArtyUARTHarnessBinder ++
  new WithArtyResetHarnessBinder ++
  new WithArtySPIFlashHarnessBinder ++
  new WithSPIFlashIOPassthrough ++
  new WithDebugResetPassthrough ++
  new WithDefaultPeripherals ++
  new freechips.rocketchip.subsystem.WithNBreakpoints(2))

class TinyRocketArtyConfig extends Config(
  new WithArtyTweaks ++
  new chipyard.TinyRocketConfig)

class ArtyWithGPIOConfig extends Config(
  new chipyard.config.WithGPIOIncludeIOF(true) ++
  new chipyard.config.WithGPIOWidth(32) ++
  new chipyard.config.WithGPIO ++
  new TinyRocketArtyConfig
)
// DOC include end: AbstractArty and Rocket
