// SPDX-License-Identifier: Apache-2.0

package spectrometer

import chisel3._
import chisel3.util._
import chisel3.iotesters.{Driver, PeekPokeTester}

import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy._

import org.scalatest.{FlatSpec, Matchers}

import java.io._

//-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------
// SPEC
//-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------
class SpectrometerTestSpec extends FlatSpec with Matchers {
  implicit val p: Parameters = Parameters.empty
  
  val fftSize = sys.props.getOrElse("fftSize", "64")
  val enablePlot = sys.props.getOrElse("enablePlot", "true") // temporary change enable plot value
  val params = (new SpectrometerTestParams(fftSize.toInt)).params
    
  //-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------
  // PLFG -> NCO -> parallel_out
  //-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------
  
  it should "test plfg -> nco -> pout streaming path" in {
    val lazyDut = LazyModule(new SpectrometerTest(params) with SpectrometerTestPins)
    chisel3.iotesters.Driver.execute(Array("--backend-name", "verilator", "--target-dir", "test_run_dir/SpectrometerTest/plfg_nco_pout", "--top-name", "SpectrometerTest"), () => lazyDut.module) {
      c => new PLFG_NCO_POUT_SpectrometerTester(lazyDut, params, enablePlot.toBoolean, true)
    } should be (true)
  }

  //-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------
  // PLFG -> NCO -> FFT -> parallel_out
  //-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------
  
  it should "test plfg -> nco -> fft -> pout streaming path" in {
    val lazyDut = LazyModule(new SpectrometerTest(params) with SpectrometerTestPins)
    chisel3.iotesters.Driver.execute(Array("--backend-name", "verilator", "--target-dir", "test_run_dir/SpectrometerTest/plfg_nco_fft_pout", "--top-name", "SpectrometerTest"), () => lazyDut.module) {
      c => new PLFG_NCO_FFT_POUT_SpectrometerTester(lazyDut, params,  enablePlot.toBoolean, true)
    } should be (true)
  }

  //-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------
  // PLFG -> NCO -> FFT -> MAG -> parallel_out
  //-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------
  
  it should "test plfg -> nco -> fft -> mag -> pout streaming path" in {
    val lazyDut = LazyModule(new SpectrometerTest(params) with SpectrometerTestPins)
    chisel3.iotesters.Driver.execute(Array("--backend-name", "verilator", "--target-dir", "test_run_dir/SpectrometerTest/plfg_nco_fft_mag_pout", "--top-name", "SpectrometerTest"), () => lazyDut.module) {
      c => new PLFG_NCO_FFT_MAG_POUT_SpectrometerTester(lazyDut, params,  enablePlot.toBoolean, true)
    } should be (true)
  }

  //-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------
  // PLFG -> NCO -> FFT -> MAG -> ACC -> parallel_out
  //-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------
  it should "test plfg -> nco -> fft -> mag -> acc -> pout streaming path" in {
    val lazyDut = LazyModule(new SpectrometerTest(params) with SpectrometerTestPins)
    chisel3.iotesters.Driver.execute(Array("--backend-name", "verilator", "--target-dir", "test_run_dir/SpectrometerTest/plfg_nco_fft_mag_acc_pout", "--top-name", "SpectrometerTest"), () => lazyDut.module) {
      c => new PLFG_NCO_FFT_MAG_ACC_POUT_SpectrometerTester(lazyDut, params,  enablePlot.toBoolean, true)
    } should be (true)
  }

  //-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------
  // PLFG -> NCO -> FFT -> MAG -> ACC -> uTx
  //-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------
  
  it should "test plfg -> nco -> fft -> mag -> acc -> utx streaming path" in {
    val lazyDut = LazyModule(new SpectrometerTest(params) with SpectrometerTestPins)
    chisel3.iotesters.Driver.execute(Array("--backend-name", "verilator", "--target-dir", "test_run_dir/SpectrometerTest/plfg_nco_fft_mag_acc_utx", "--top-name", "SpectrometerTest"), () => lazyDut.module) {
      c => new PLFG_NCO_FFT_MAG_ACC_UTX_SpectrometerTester(lazyDut, params,  enablePlot.toBoolean, true)
    } should be (true)
  }

// //-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------
//  PIN -> POUT
//-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------

  it should "test pin -> pout streaming path" in {
    val lazyDut = LazyModule(new SpectrometerTest(params) with SpectrometerTestPins)
    chisel3.iotesters.Driver.execute(Array("--backend-name", "verilator", "--target-dir", "test_run_dir/SpectrometerTest/pin_pout", "--top-name", "SpectrometerTest"), () => lazyDut.module) {
      c => new PIN_POUT_SpectrometerTester(lazyDut, params,  enablePlot.toBoolean, true)
    } should be (true)
  }


//   //-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------
//  PIN -> FFT -> POUT
//-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------
  it should "test pin -> fft -> pout streaming path" in {
    val lazyDut = LazyModule(new SpectrometerTest(params) with SpectrometerTestPins)
    chisel3.iotesters.Driver.execute(Array("--backend-name", "verilator", "--target-dir", "test_run_dir/SpectrometerTest/pin_fft_pout", "--top-name", "SpectrometerTest"), () => lazyDut.module) {
      c => new PIN_FFT_POUT_SpectrometerTester(lazyDut, params,  enablePlot.toBoolean, true)
    } should be (true)
  }

//   //-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------
//  PIN -> FFT -> MAG -> POUT
//-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------
  it should "test pin -> fft -> mag -> pout streaming path" in {
    val lazyDut = LazyModule(new SpectrometerTest(params) with SpectrometerTestPins)
    chisel3.iotesters.Driver.execute(Array("--backend-name", "verilator", "--target-dir", "test_run_dir/SpectrometerTest/pin_fft_mag_pout", "--top-name", "SpectrometerTest"), () => lazyDut.module) {
      c => new PIN_FFT_MAG_POUT_SpectrometerTester(lazyDut, params,  enablePlot.toBoolean, true)
    } should be (true)
  }

//   //-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------
//  PIN -> FFT -> MAG -> ACC -> POUT
//-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------
  it should "test pin -> fft -> mag -> acc -> pout streaming path" in {
    val lazyDut = LazyModule(new SpectrometerTest(params) with SpectrometerTestPins)
    chisel3.iotesters.Driver.execute(Array("--backend-name", "verilator", "--target-dir", "test_run_dir/SpectrometerTest/pin_fft_mag_acc_pout", "--top-name", "SpectrometerTest"), () => lazyDut.module) {
      c => new PIN_FFT_MAG_ACC_POUT_SpectrometerTester(lazyDut, params,  enablePlot.toBoolean, true)
    } should be (true)
  }

// Check this one
//-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------
// PIN -> NCO -> FFT -> MAG -> ACC -> POUT
//-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------  
  it should "test pin -> nco -> mag -> acc -> pout streaming path " in {
    val lazyDut = LazyModule(new SpectrometerTest(params) with SpectrometerTestPins)
    chisel3.iotesters.Driver.execute(Array("--backend-name", "verilator", "--target-dir", "test_run_dir/SpectrometerTest/pin_nco_fft_mag_acc_pout", "--top-name", "SpectrometerTest"), () => lazyDut.module) {
      c => new PIN_NCO_FFT_MAG_ACC_POUT_SpectrometerTester(lazyDut, params, enablePlot.toBoolean, true)
    } should be (true)
  }
}
