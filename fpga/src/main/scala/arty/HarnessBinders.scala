package chipyard.fpga.arty

import chisel3._

import freechips.rocketchip.config.{Parameters}
import freechips.rocketchip.devices.debug._
import freechips.rocketchip.jtag.{JTAGIO}
import freechips.rocketchip.subsystem._

import chipyard.iobinders.GetSystemParameters

import sifive.blocks.devices.uart._
import sifive.blocks.devices.jtag._
import sifive.blocks.devices.spi._
import sifive.blocks.devices.pinctrl._
import sifive.blocks.devices.gpio._

import sifive.fpgashells.ip.xilinx.{IBUFG, IOBUF, PULLUP, PowerOnResetFPGAOnly}

import chipyard.harness.{ComposeHarnessBinder, OverrideHarnessBinder}

class WithArtyResetHarnessBinder extends ComposeHarnessBinder({
  (system: HasPeripheryDebugModuleImp, th: ArtyFPGATestHarness, ports: Seq[Bool]) => {
    require(ports.size == 2)

    withClockAndReset(th.harnessClock, th.harnessReset) {
      // Debug module reset
      th.dut_ndreset := ports(0)

      // JTAG reset
      ports(1) := PowerOnResetFPGAOnly(th.harnessClock)
    }
  }
})

class WithArtyJTAGHarnessBinder extends OverrideHarnessBinder({
  (system: HasPeripheryDebug, th: ArtyFPGATestHarness, ports: Seq[Data]) => {
    ports.map {
      case j: JTAGIO =>
        withClockAndReset(th.harnessClock, th.harnessReset) {
          val io_jtag = Wire(new JTAGPins(() => new BasePin(), false)).suggestName("jtag")

          JTAGPinsFromPort(io_jtag, j)

          io_jtag.TCK.i.ival := IBUFG(IOBUF(th.jd_2).asClock).asBool

          IOBUF(th.jd_5, io_jtag.TMS)
          PULLUP(th.jd_5)

          IOBUF(th.jd_4, io_jtag.TDI)
          PULLUP(th.jd_4)

          IOBUF(th.jd_0, io_jtag.TDO)

          // mimic putting a pullup on this line (part of reset vote)
          th.SRST_n := IOBUF(th.jd_6)
          PULLUP(th.jd_6)

          // ignore the po input
          io_jtag.TCK.i.po.map(_ := DontCare)
          io_jtag.TDI.i.po.map(_ := DontCare)
          io_jtag.TMS.i.po.map(_ := DontCare)
          io_jtag.TDO.i.po.map(_ := DontCare)
        }
    }
  }
})

class WithArtyUARTHarnessBinder extends OverrideHarnessBinder({
  (system: HasPeripheryUARTModuleImp, th: ArtyFPGATestHarness, ports: Seq[UARTPortIO]) => {
    withClockAndReset(th.harnessClock, th.harnessReset) {
      IOBUF(th.uart_rxd_out,  ports.head.txd)
      ports.head.rxd := IOBUF(th.uart_txd_in)
    }
  }
})

class WithArtySPIFlashHarnessBinder extends OverrideHarnessBinder({
  (system: HasPeripherySPIFlashModuleImp, th: ArtyFPGATestHarness, ports: Seq[SPIPortIO]) => {
    withClockAndReset(th.harnessClock, th.harnessReset) {
      implicit val p: Parameters = GetSystemParameters(system)

      val io_qspi = Wire(new SPIPins(() => new BasePin(), p(PeripherySPIFlashKey)(0))).suggestName("qspi")

      SPIPinsFromPort(io_qspi, ports(0), clock = th.harnessClock, reset = th.harnessReset.asBool, syncStages = 3)

      IOBUF(th.qspi_cs, io_qspi.cs(0))
      IOBUF(th.qspi_sck, io_qspi.sck)
      IOBUF(th.qspi_dq(0), io_qspi.dq(0))
      IOBUF(th.qspi_dq(1), io_qspi.dq(1))
      IOBUF(th.qspi_dq(2), io_qspi.dq(2))
      IOBUF(th.qspi_dq(3), io_qspi.dq(3))

      // ignore the po input
      io_qspi.cs(0).i.po.map(_ := DontCare)
      io_qspi.sck.i.po.map(_ := DontCare)
      io_qspi.dq(0).i.po.map(_ := DontCare)
      io_qspi.dq(1).i.po.map(_ := DontCare)
      io_qspi.dq(2).i.po.map(_ := DontCare)
      io_qspi.dq(3).i.po.map(_ := DontCare)
    }
  }
})
class WithArtyGPIOHarnessBinder extends OverrideHarnessBinder({
  (system: HasPeripheryGPIOModuleImp, th: ArtyFPGATestHarness, ports: Seq[GPIOPortIO]) => {
    withClockAndReset(th.harnessClock, th.harnessReset) {
      implicit val p: Parameters = GetSystemParameters(system)

    //Only generate connections if GPIO exists
    if(!p(PeripheryGPIOKey).isEmpty){
      //This HarnessBinder was created under the assumption of a 32-pin GPIO Port
      require((p(PeripheryGPIOKey)(0)).width == 32)

      val io_gpio = Wire(new GPIOPins(() => new BasePin(), p(PeripheryGPIOKey)(0))).suggestName("gpio")
      GPIOPinsFromPort(io_gpio, ports(0), clock = th.harnessClock, reset = th.harnessReset.asBool)

      
      // Shield header row 0: PD2-PD7
      IOBUF(th.ck_io(2),  io_gpio.pins(18))
      IOBUF(th.ck_io(3),  io_gpio.pins(19)) // PWM1(1)
      IOBUF(th.ck_io(4),  io_gpio.pins(20)) // PWM1(0)
      IOBUF(th.ck_io(5),  io_gpio.pins(21)) // PWM1(2)
      IOBUF(th.ck_io(6),  io_gpio.pins(22)) // PWM1(3)
      IOBUF(th.ck_io(7),  io_gpio.pins(23))

      // Header row 1: PB0-PB5
      IOBUF(th.ck_io(8),  io_gpio.pins(0))  // PWM0(0)
      IOBUF(th.ck_io(9),  io_gpio.pins(1))  // PWM0(1)
      IOBUF(th.ck_io(10), io_gpio.pins(2))  // SPI CS(0) / PWM0(2)
      IOBUF(th.ck_io(11), io_gpio.pins(3))  // SPI MOSI  / PWM0(3)
      IOBUF(th.ck_io(12), io_gpio.pins(4))  // SPI MISO
      IOBUF(th.ck_io(13), io_gpio.pins(5))  // SPI SCK

      io_gpio.pins(6).i.ival  := 0.U
      io_gpio.pins(7).i.ival  := 0.U
      io_gpio.pins(8).i.ival  := 0.U

      // Header row 3: A0-A5 (we don't support using them as analog inputs)
      // just treat them as regular digital GPIOs
      IOBUF(th.ck_io(15), io_gpio.pins(9))  // A1 = CS(2)
      IOBUF(th.ck_io(16), io_gpio.pins(10)) // A2 = CS(3) / PWM2(0)
      IOBUF(th.ck_io(17), io_gpio.pins(11)) // A3 = PWM2(1)
      IOBUF(th.ck_io(18), io_gpio.pins(12)) // A4 = PWM2(2) / SDA
      IOBUF(th.ck_io(19), io_gpio.pins(13)) // A5 = PWM2(3) / SCL

      // Mirror outputs of GPIOs with PWM peripherals to RGB LEDs on Arty
      // assign RGB LED0 R,G,B inputs = PWM0(1,2,3) when iof_1 is active
      IOBUF(th.led0_r, io_gpio.pins(1))
      IOBUF(th.led0_g, io_gpio.pins(2))
      IOBUF(th.led0_b, io_gpio.pins(3))

      // Note that this is the one which is actually connected on the HiFive/Crazy88
      // Board. Same with RGB LED1 R,G,B inputs = PWM1(1,2,3) when iof_1 is active
      IOBUF(th.led1_r, io_gpio.pins(19))
      IOBUF(th.led1_g, io_gpio.pins(21))
      IOBUF(th.led1_b, io_gpio.pins(22))

      // and RGB LED2 R,G,B inputs = PWM2(1,2,3) when iof_1 is active
      IOBUF(th.led2_r, io_gpio.pins(11))
      IOBUF(th.led2_g, io_gpio.pins(12))
      IOBUF(th.led2_b, io_gpio.pins(13))

      // Only 19 out of 20 shield pins connected to GPIO pins
      // Shield pin A5 (pin 14) left unconnected
      // The buttons are connected to some extra GPIO pins not connected on the
      // HiFive1 board
      IOBUF(th.btn_0, io_gpio.pins(15))
      IOBUF(th.btn_1, io_gpio.pins(30))
      IOBUF(th.btn_2, io_gpio.pins(31))


      // These are being set to 0 so that the design will compile...not sure why
      // the SiFive Freedom Arty Example did not use them...
      io_gpio.pins(24).i.ival := 0.U
      io_gpio.pins(27).i.ival := 0.U
      io_gpio.pins(14).i.ival := 0.U
      io_gpio.pins(29).i.ival := 0.U
      io_gpio.pins(17).i.ival := 0.U
      io_gpio.pins(26).i.ival := 0.U
      io_gpio.pins(28).i.ival := 0.U
      io_gpio.pins(16).i.ival := 0.U
      io_gpio.pins(25).i.ival := 0.U

      io_gpio.pins.foreach {x => x.i.po.map(_ := DontCare)}
    }
    


    }
  }
})
