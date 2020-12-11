package spectrometerLA

import freechips.rocketchip.interrupts._
import dsptools._
import dsptools.numbers._
import chisel3._
import chisel3.util._
import chisel3.experimental._
import chisel3.iotesters.{Driver, PeekPokeTester}

import dspblocks.{AXI4DspBlock, AXI4StandaloneBlock, TLDspBlock, TLStandaloneBlock}
import freechips.rocketchip.amba.axi4stream._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.system.BaseConfig

import org.scalatest.{FlatSpec, Matchers}
import breeze.math.Complex
import breeze.signal.{fourierTr, iFourierTr}
import breeze.linalg._
import breeze.plot._

import plfg._
import nco._
import fft._
import uart._
import splitter._
import magnitude._
import accumulator._

import java.io._

// perhaps some of the inclusions are not necessary

//-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------
// SPEC
//-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------
class SpectrometerTestSpec extends FlatSpec with Matchers {
  implicit val p: Parameters = Parameters.empty

  val fftSize = sys.props.getOrElse("fftSize", "64")

  val enablePlot = sys.props.getOrElse("enablePlot", "false")

  // here just define parameters
  val params = (new SpectrometerTestParams(fftSize.toInt)).params
    

  //-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------
  // PLFG -> NCO -> FFT -> MAG -> ACC -> parallel_out
  //-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------

  behavior of "PLFG_NCO_FFT_MAG_ACC_POUT_Spectrometer" 
  
  it should "work" in {
    
    val lazyDut = LazyModule(new SpectrometerTest(params) with SpectrometerTestPins)
    chisel3.iotesters.Driver.execute(Array("-tiwv", "-tbn", "verilator", "-tivsuv", "--target-dir", "test_run_dir/SpectrometerTestWithLAoutputs/plfg_nco_fft_mag_acc_pout", "--top-name", "SpectrometerTest"), () => lazyDut.module) {
      c => new PLFG_NCO_FFT_MAG_ACC_POUT_SpectrometerTester(lazyDut, params, enablePlot.toBoolean, true)
    } should be (true)
  }

  //-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------
  // PLFG -> NCO -> FFT -> MAG -> ACC -> uTx
  //-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------

  behavior of "PLFG_NCO_FFT_MAG_ACC_UTX_Spectrometer" 
  
  it should "work" in {
    
    val lazyDut = LazyModule(new SpectrometerTest(params) with SpectrometerTestPins)
    chisel3.iotesters.Driver.execute(Array("-tiwv", "-tbn", "verilator", "-tivsuv", "--target-dir", "test_run_dir/SpectrometerTestWithLAoutputs/plfg_nco_fft_mag_acc_utx", "--top-name", "SpectrometerTest"), () => lazyDut.module) {
      c => new PLFG_NCO_FFT_MAG_ACC_UTX_SpectrometerTester(lazyDut, params, enablePlot.toBoolean, true)
    } should be (true)
  }

//-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------
// PIN -> FFT -> MAG -> ACC -> POUT
//-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------
  behavior of "PIN_FFT_MAG_ACC_POUT_Spectrometer" 
  
  it should "work" in {

    val lazyDut = LazyModule(new SpectrometerTest(params) with SpectrometerTestPins)
    chisel3.iotesters.Driver.execute(Array("-tiwv", "-tbn", "verilator", "-tivsuv", "--target-dir", "test_run_dir/SpectrometerTestWithLAoutputs/pin_fft_mag_acc_pout", "--top-name", "SpectrometerTest"), () => lazyDut.module) {
      c => new PIN_FFT_MAG_ACC_POUT_SpectrometerTester(lazyDut, params, enablePlot.toBoolean, true)
    } should be (true)
  }

//-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------
// PIN -> NCO -> FFT -> MAG -> ACC -> POUT
//-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------
  behavior of "PIN_NCO_FFT_MAG_ACC_POUT_Spectrometer" 
  
  it should "work" in {

    val lazyDut = LazyModule(new SpectrometerTest(params) with SpectrometerTestPins)
    chisel3.iotesters.Driver.execute(Array("-tiwv", "-tbn", "verilator", "-tivsuv", "--target-dir", "test_run_dir/SpectrometerTestWithLAoutputs/pin_nco_fft_mag_acc_pout", "--top-name", "SpectrometerTest"), () => lazyDut.module) {
      c => new PIN_NCO_FFT_MAG_ACC_POUT_SpectrometerTester(lazyDut, params, enablePlot.toBoolean, true)
    } should be (true)
  }
  
}


