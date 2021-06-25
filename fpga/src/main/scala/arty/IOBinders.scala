package chipyard.fpga.arty

import chisel3._
import chisel3.experimental.{IO}

import freechips.rocketchip.util._
import freechips.rocketchip.devices.debug._

import sifive.blocks.devices.spi._
import sifive.blocks.devices.gpio._

import barstools.iocell.chisel._

import chipyard.iobinders.{ComposeIOBinder, OverrideIOBinder, IOCellKey}

class WithDebugResetPassthrough extends ComposeIOBinder({
  (system: HasPeripheryDebugModuleImp) => {
    // Debug module reset
    val io_ndreset: Bool = IO(Output(Bool())).suggestName("ndreset")
    io_ndreset := system.debug.get.ndreset

    // JTAG reset
    val sjtag = system.debug.get.systemjtag.get
    val io_sjtag_reset: Bool = IO(Input(Bool())).suggestName("sjtag_reset")
    sjtag.reset := io_sjtag_reset

    (Seq(io_ndreset, io_sjtag_reset), Nil)
  }
})

class WithSPIFlashIOPassthrough  extends OverrideIOBinder({
  (system: HasPeripherySPIFlashModuleImp) => {
    val (ports: Seq[SPIPortIO], cells2d) = system.qspi.zipWithIndex.map({ case (u, i) =>
      val (port, ios) = IOCell.generateIOFromSignal(u, s"qspi_${i}", system.p(IOCellKey), abstractResetAsAsync = true)
      (port, ios)
    }).unzip
    (ports, cells2d.flatten)
  }
})