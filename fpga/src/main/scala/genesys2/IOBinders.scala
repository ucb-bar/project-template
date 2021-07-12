package chipyard.fpga.genesys2

import chipyard.CanHaveMasterTLMemPort
import chipyard.iobinders.{GetSystemParameters, OverrideIOBinder, OverrideLazyIOBinder}
import chisel3._
import chisel3.experimental.{DataMirror, IO}
import freechips.rocketchip.devices.debug._
import freechips.rocketchip.diplomacy.InModuleBody
import freechips.rocketchip.prci.{ClockSinkNode, ClockSinkParameters}
import freechips.rocketchip.subsystem.BaseSubsystem
import freechips.rocketchip.tilelink.TLBundle
import freechips.rocketchip.util.{HeterogeneousBag, PSDTestMode}
import sifive.blocks.devices.uart.HasPeripheryUARTModuleImp

class WithUARTIOPassthrough extends OverrideIOBinder({
  (system: HasPeripheryUARTModuleImp) => {
    val io_uart_pins_temp = system.uart.zipWithIndex.map { case (dio, i) => IO(dio.cloneType).suggestName(s"uart_$i") }
    (io_uart_pins_temp zip system.uart).foreach { case (io, sysio) =>
      io <> sysio
    }
    (io_uart_pins_temp, Nil)
  }
})

class WithJTAGIOPassthrough extends OverrideLazyIOBinder({
  (system: HasPeripheryDebug) => {
    implicit val p = GetSystemParameters(system)
    val tlbus = system.asInstanceOf[BaseSubsystem].locateTLBusWrapper(p(ExportDebug).slaveWhere)
    val clockSinkNode = system.debugOpt.map(_ => ClockSinkNode(Seq(ClockSinkParameters())))
    clockSinkNode.map(_ := tlbus.fixedClockNode)
    def clockBundle = clockSinkNode.get.in.head._1

    InModuleBody { system.asInstanceOf[BaseSubsystem].module match { case system: HasPeripheryDebugModuleImp => {
      system.debug.map({ debug =>
        // We never use the PSDIO, so tie it off on-chip
        system.psd.psd.foreach { _ <> 0.U.asTypeOf(new PSDTestMode) }
        system.resetctrl.map { rcio => rcio.hartIsInReset.map { _ := clockBundle.reset.asBool } }
        system.debug.map { d =>
          // Tie off extTrigger
          d.extTrigger.foreach { t =>
            t.in.req := false.B
            t.out.ack := t.out.req
          }
          // Tie off disableDebug
          d.disableDebug.foreach { d => d := false.B }
          // Drive JTAG on-chip IOs
          d.systemjtag.map { j =>
            j.reset := clockBundle.reset
            j.mfr_id := p(JtagDTMKey).idcodeManufId.U(11.W)
            j.part_number := p(JtagDTMKey).idcodePartNum.U(16.W)
            j.version := p(JtagDTMKey).idcodeVersion.U(4.W)
          }
        }
        Debug.connectDebugClockAndReset(Some(debug), clockBundle.clock)

        val jtagPins = debug.systemjtag.map { j =>
          val io_jtag_pins_temp = IO(Flipped(j.jtag.cloneType)).suggestName(s"debug_jtag")
          io_jtag_pins_temp <> j.jtag
          io_jtag_pins_temp
        }.get

        (Seq(jtagPins), Nil)
      }).getOrElse((Nil, Nil))
    }}}
  }
})

class WithTLIOPassthrough extends OverrideIOBinder({
  (system: CanHaveMasterTLMemPort) => {
    val io_tl_mem_pins_temp = IO(DataMirror.internal.chiselTypeClone[HeterogeneousBag[TLBundle]](system.mem_tl)).suggestName("tl_slave")
    io_tl_mem_pins_temp <> system.mem_tl
    (Seq(io_tl_mem_pins_temp), Nil)
  }
})
