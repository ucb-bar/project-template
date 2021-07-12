package chipyard.fpga.genesys2

import chipyard.harness.ApplyHarnessBinders
import chipyard.iobinders.HasIOBinders
import chipyard._
import chisel3._
import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import sifive.blocks.devices.uart._
import sifive.fpgashells.clocks._
import sifive.fpgashells.ip.xilinx._
import sifive.fpgashells.shell._
import sifive.fpgashells.shell.xilinx._

case object FPGAFrequencyKey extends Field[Double](100.0)

class Genesys2FPGATestHarness(override implicit val p: Parameters) extends Genesys2ShellBasicOverlays {

  def dp = designParameters

  val jtag_location = Some("PMOD_JA")

  // Order matters; ddr depends on sys_clock
  val uart      = Overlay(UARTOverlayKey, new UARTGenesys2ShellPlacer(this, UARTShellInput()))
  val jtag      = Overlay(JTAGDebugOverlayKey, new JTAGDebugGenesys2ShellPlacer(this, JTAGDebugShellInput(location = jtag_location)))

  val topDesign = LazyModule(p(BuildTop)(dp)).suggestName("chiptop")

  // place all clocks in the shell
  require(dp(ClockInputOverlayKey).nonEmpty)
  val sysClkNode = dp(ClockInputOverlayKey).head.place(ClockInputDesignInput()).overlayOutput.node

  /*** Connect/Generate clocks ***/

  // connect to the PLL that will generate multiple clocks
  val harnessSysPLL = dp(PLLFactoryKey)()
  harnessSysPLL := sysClkNode

  // create and connect to the dutClock
  val dutClock = ClockSinkNode(freqMHz = dp(FPGAFrequencyKey))
  val dutWrangler = LazyModule(new ResetWrangler)
  val dutGroup = ClockGroup()
  dutClock := dutWrangler.node := dutGroup := harnessSysPLL

  /*** UART ***/
  val io_uart_bb = BundleBridgeSource(() => new UARTPortIO(dp(PeripheryUARTKey).head))
  dp(UARTOverlayKey).head.place(UARTDesignInput(io_uart_bb))

  /*** JTAG ***/
  val io_jtag = dp(JTAGDebugOverlayKey).head.place(JTAGDebugDesignInput()).overlayOutput.jtag

  /*** DDR ***/
  val ddrNode = dp(DDROverlayKey).head.place(DDRDesignInput(dp(ExtTLMem).get.master.base, dutWrangler.node, harnessSysPLL)).overlayOutput.ddr

  // connect 1 mem. channel to the FPGA DDR
  val inParams = topDesign match { case td: ChipTop =>
    td.lazySystem match { case lsys: CanHaveMasterTLMemPort =>
      lsys.memTLNode.edges.in.head
    }
  }
  val ddrClient = TLClientNode(Seq(inParams.master))
  ddrNode := ddrClient

  // module implementation
  override lazy val module = new Genesys2FPGATestHarnessImp(this)
}

class Genesys2FPGATestHarnessImp(_outer: Genesys2FPGATestHarness) extends LazyRawModuleImp(_outer) with HasHarnessSignalReferences {

  val genesys2Outer = _outer

  val reset_n = IO(Input(Bool()))
  _outer.xdc.addPackagePin(reset_n, "R19")
  _outer.xdc.addIOStandard(reset_n, "LVCMOS33")

  val resetIBUF = Module(new IBUF)
  resetIBUF.io.I := ~reset_n

  val sysclk: Clock = _outer.sysClkNode.out.head._1.clock

  val powerOnReset: Bool = PowerOnResetFPGAOnly(sysclk)
  _outer.sdc.addAsyncPath(Seq(powerOnReset))


  _outer.pllReset := (resetIBUF.io.O || powerOnReset)

  // reset setup
  val hReset = Wire(Reset())
  hReset := _outer.dutClock.in.head._1.reset

  val harnessClock = _outer.dutClock.in.head._1.clock
  val harnessReset = WireInit(hReset)
  val dutReset = hReset.asAsyncReset()
  val success = false.B

  childClock := harnessClock
  childReset := harnessReset

  // harness binders are non-lazy
  _outer.topDesign match { case d: HasTestHarnessFunctions =>
    d.harnessFunctions.foreach(_(this))
  }
  _outer.topDesign match { case d: HasIOBinders =>
    ApplyHarnessBinders(this, d.lazySystem, d.portMap)
  }
}
