package chipyard.fpga.genesys2

import chipyard.harness.OverrideHarnessBinder
import chipyard.{CanHaveMasterTLMemPort, HasHarnessSignalReferences}
import chisel3._
import chisel3.experimental.BaseModule
import freechips.rocketchip.devices.debug.HasPeripheryDebug
import freechips.rocketchip.jtag.JTAGIO
import freechips.rocketchip.tilelink.TLBundle
import freechips.rocketchip.util.HeterogeneousBag
import sifive.blocks.devices.uart.{HasPeripheryUARTModuleImp, UARTPortIO}

/*** UART ***/
class WithUART extends OverrideHarnessBinder({
  (system: HasPeripheryUARTModuleImp, th: BaseModule with HasHarnessSignalReferences, ports: Seq[UARTPortIO]) => {
    th match { case genesys2th: Genesys2FPGATestHarnessImp => {
      genesys2th.genesys2Outer.io_uart_bb.bundle <> ports.head
    } }
  }
})

class WithJTAG extends OverrideHarnessBinder({
  (system: HasPeripheryDebug, th: HasHarnessSignalReferences, ports: Seq[JTAGIO]) => {
    th match { case genesys2th: Genesys2FPGATestHarnessImp => {
      val j = ports.head
      val o = genesys2th.genesys2Outer.io_jtag
      j.TCK := o.TCK
      j.TDI := o.TDI
      j.TMS := o.TMS
      o.TDO <> j.TDO
    } }
  }
})

/*** Experimental DDR ***/
class WithDDRMem extends OverrideHarnessBinder({
  (system: CanHaveMasterTLMemPort, th: BaseModule with HasHarnessSignalReferences, ports: Seq[HeterogeneousBag[TLBundle]]) => {
    th match { case genesys2th: Genesys2FPGATestHarnessImp => {
      require(ports.size == 1)

      val bundles = genesys2th.genesys2Outer.ddrClient.out.map(_._1)
      val ddrClientBundle = Wire(new HeterogeneousBag(bundles.map(_.cloneType)))
      bundles.zip(ddrClientBundle).foreach { case (bundle, io) => bundle <> io }
      ddrClientBundle <> ports.head
    } }
  }
})
