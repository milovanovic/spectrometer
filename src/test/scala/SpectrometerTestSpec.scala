package spectrometer

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

trait SpectrometerTestPins extends SpectrometerTest {
  // Generate AXI4 slave output
  def standaloneParams = AXI4BundleParameters(addrBits = 32, dataBits = 32, idBits = 1)
  val ioMem = mem.map { m => {
    val ioMemNode = BundleBridgeSource(() => AXI4Bundle(standaloneParams))
    m := BundleBridgeToAXI4(AXI4MasterPortParameters(Seq(AXI4MasterParameters("bundleBridgeToAXI4")))) := ioMemNode
    val ioMem = InModuleBody { ioMemNode.makeIO() }
    ioMem
  }}

  // Generate AXI-stream output
  val ioStreamNode = BundleBridgeSink[AXI4StreamBundle]()
  ioStreamNode := AXI4StreamToBundleBridge(AXI4StreamSlaveParameters()) := out_adapt
  val outStream = InModuleBody { ioStreamNode.makeIO() }

  val ioparallelin = BundleBridgeSource(() => new AXI4StreamBundle(AXI4StreamBundleParameters(n = 1)))
  in_queue.node := BundleBridgeToAXI4Stream(AXI4StreamMasterParameters(n = 1)) := ioparallelin
  val inStream = InModuleBody { ioparallelin.makeIO() }
}

//-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------
// SPEC
//-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------
class SpectrometerTestSpec extends FlatSpec with Matchers {
  implicit val p: Parameters = Parameters.empty

  // here just define parameters
  val params = SpectrometerTestParameters (
    plfgParams = FixedPLFGParams(
      maxNumOfSegments = 4,
      maxNumOfDifferentChirps = 8,
      maxNumOfRepeatedChirps = 8,
      maxChirpOrdinalNum = 4,
      maxNumOfFrames = 4,
      maxNumOfSamplesWidth = 8,
      outputWidthInt = 16,
      outputWidthFrac = 0
    ),
    ncoParams = FixedNCOParams(
      tableSize = 128,
      tableWidth = 16,
      phaseWidth = 9,
      rasterizedMode = false,
      nInterpolationTerms = 0,
      ditherEnable = false,
      syncROMEnable = false,
      phaseAccEnable = true,
      roundingMode = RoundHalfUp,
      pincType = Streaming,
      poffType = Fixed
	  ),
    fftParams = FFTParams.fixed(
      dataWidth = 16,
      twiddleWidth = 16,
      numPoints = 512,
      useBitReverse = false,
      runTime = true,
      numAddPipes = 1,
      numMulPipes = 1,
      expandLogic = Array.fill(log2Up(512))(0),
      keepMSBorLSB = Array.fill(log2Up(512))(true),
      minSRAMdepth = 512,
      binPoint = 0
    ),
    magParams = MAGParams.fixed(
      dataWidth       = 16,
      binPoint        = 0,
      dataWidthLog    = 16,
      binPointLog     = 9,
      log2LookUpWidth = 9,
      useLast         = true,
      numAddPipes     = 1,
      numMulPipes     = 1
    ),
    accParams = AccParams(
      proto = FixedPoint(16.W, 0.BP),
      protoAcc = FixedPoint(32.W, 0.BP),
      accDepth = 512
    ),
    inSplitAddress   = AddressSet(0x30000000, 0xF),
    plfgRAM          = AddressSet(0x30001000, 0xFFF),
    plfgAddress      = AddressSet(0x30002100, 0xFF),
    plfgSplitAddress = AddressSet(0x30002200, 0xF),
    plfgMuxAddress0  = AddressSet(0x30002210, 0xF),
    plfgMuxAddress1  = AddressSet(0x30002220, 0xF),
    ncoAddress       = AddressSet(0x30003000, 0xF),
    ncoSplitAddress  = AddressSet(0x30003100, 0xF),
    ncoMuxAddress0   = AddressSet(0x30003110, 0xF),
    ncoMuxAddress1   = AddressSet(0x30003120, 0xF),
    fftAddress       = AddressSet(0x30004000, 0xFF),
    fftSplitAddress  = AddressSet(0x30004100, 0xF),
    fftMuxAddress0   = AddressSet(0x30004110, 0xF),
    fftMuxAddress1   = AddressSet(0x30004120, 0xF),
    magAddress       = AddressSet(0x30005000, 0xFF),
    magSplitAddress  = AddressSet(0x30005100, 0xF),
    magMuxAddress0   = AddressSet(0x30005110, 0xF),
    magMuxAddress1   = AddressSet(0x30005120, 0xF),
    accQueueBase     =            0x30006000,
    accAddress       = AddressSet(0x30007000, 0xF),
    outMuxAddress    = AddressSet(0x30008000, 0xF),
    uartParams       = UARTParams(address = 0x30009000, nTxEntries = 256, nRxEntries = 256),
    uRxSplitAddress  = AddressSet(0x30009100, 0xF),
    divisorInit      = (173).toInt,
    beatBytes        = 4)
    
  //-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------
  // PLFG -> NCO -> parallel_out
  //-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------
  behavior of "PLFG_NCO_POUT_Spectrometer" 
  
  it should "work" in {
    
    val lazyDut = LazyModule(new SpectrometerTest(params) with SpectrometerTestPins)
    chisel3.iotesters.Driver.execute(Array("-tiwv", "-tbn", "verilator", "-tivsuv", "--target-dir", "test_run_dir/SpectrometerTest/plfg_nco_pout", "--top-name", "SpectrometerTest"), () => lazyDut.module) {
      c => new PLFG_NCO_POUT_SpectrometerTester(lazyDut, params, true)
    } should be (true)
  }

  //-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------
  // PLFG -> NCO -> FFT -> parallel_out
  //-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------
  behavior of "PLFG_NCO_FFT_POUT_Spectrometer" 
  
  it should "work" in {
  
    val lazyDut = LazyModule(new SpectrometerTest(params) with SpectrometerTestPins)
    chisel3.iotesters.Driver.execute(Array("-tiwv", "-tbn", "verilator", "-tivsuv", "--target-dir", "test_run_dir/SpectrometerTest/plfg_nco_fft_pout", "--top-name", "SpectrometerTest"), () => lazyDut.module) {
      c => new PLFG_NCO_FFT_POUT_SpectrometerTester(lazyDut, params, true)
    } should be (true)
  }

  //-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------
  // PLFG -> NCO -> FFT -> MAG -> parallel_out
  //-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------

  behavior of "PLFG_NCO_FFT_MAG_POUT_Spectrometer" 
  
  it should "work" in {
    
    val lazyDut = LazyModule(new SpectrometerTest(params) with SpectrometerTestPins)
    chisel3.iotesters.Driver.execute(Array("-tiwv", "-tbn", "verilator", "-tivsuv", "--target-dir", "test_run_dir/SpectrometerTest/plfg_nco_fft_mag_pout", "--top-name", "SpectrometerTest"), () => lazyDut.module) {
      c => new PLFG_NCO_FFT_MAG_POUT_SpectrometerTester(lazyDut, params, true)
    } should be (true)
  }

  //-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------
  // PLFG -> NCO -> FFT -> MAG -> ACC -> parallel_out
  //-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------

  behavior of "PLFG_NCO_FFT_MAG_ACC_POUT_Spectrometer" 
  
  it should "work" in {
    
    val lazyDut = LazyModule(new SpectrometerTest(params) with SpectrometerTestPins)
    chisel3.iotesters.Driver.execute(Array("-tiwv", "-tbn", "verilator", "-tivsuv", "--target-dir", "test_run_dir/SpectrometerTest/plfg_nco_fft_mag_acc_pout", "--top-name", "SpectrometerTest"), () => lazyDut.module) {
      c => new PLFG_NCO_FFT_MAG_ACC_POUT_SpectrometerTester(lazyDut, params, true)
    } should be (true)
  }

// //-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------
//   // PIN -> POUT
//   //-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------
//   behavior of "PIN_POUT_Spectrometer" 
  
//   it should "work" in {
  
//     val lazyDut = LazyModule(new SpectrometerTest(params) with SpectrometerTestPins)
//     chisel3.iotesters.Driver.execute(Array("-tiwv", "-tbn", "verilator", "-tivsuv", "--target-dir", "test_run_dir/SpectrometerTest/PIN_POUT", "--top-name", "SpectrometerTest"), () => lazyDut.module) {
//       c => new PIN_POUT_SpectrometerTester(lazyDut, params, true)
//     } should be (true)
//   }


//   //-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------
//   // PIN -> FFT -> POUT
//   //-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------
//   behavior of "PIN_FFT_POUT_Spectrometer" 
  
//   it should "work" in {

//     val lazyDut = LazyModule(new SpectrometerTest(params) with SpectrometerTestPins)
//     chisel3.iotesters.Driver.execute(Array("-tiwv", "-tbn", "verilator", "-tivsuv", "--target-dir", "test_run_dir/SpectrometerTest/PIN_FFT_POUT", "--top-name", "SpectrometerTest"), () => lazyDut.module) {
//       c => new PIN_FFT_POUT_SpectrometerTester(lazyDut, params, true)
//     } should be (true)
//   }

//   //-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------
//   // PIN -> FFT -> MAG -> POUT
//   //-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------
//   behavior of "PIN_FFT_MAG_POUT_Spectrometer" 
  
//   it should "work" in {

//     val lazyDut = LazyModule(new SpectrometerTest(params) with SpectrometerTestPins)
//     chisel3.iotesters.Driver.execute(Array("-tiwv", "-tbn", "verilator", "-tivsuv", "--target-dir", "test_run_dir/SpectrometerTest/PIN_FFT_MAG_POUT", "--top-name", "SpectrometerTest"), () => lazyDut.module) {
//       c => new PIN_FFT_MAG_POUT_SpectrometerTester(lazyDut, params, true)
//     } should be (true)
//   }

//   //-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------
// //   // PIN -> FFT -> MAG -> ACC -> POUT
// //   //-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------
//   behavior of "PIN_FFT_MAG_ACC_POUT_Spectrometer" 
  
//   it should "work" in {

//     val lazyDut = LazyModule(new SpectrometerTest(params) with SpectrometerTestPins)
//     chisel3.iotesters.Driver.execute(Array("-tiwv", "-tbn", "verilator", "-tivsuv", "--target-dir", "test_run_dir/SpectrometerTest/PIN_FFT_MAG_ACC_POUT", "--top-name", "SpectrometerTest"), () => lazyDut.module) {
//       c => new PIN_FFT_MAG_ACC_POUT_SpectrometerTester(lazyDut, params, true)
//     } should be (true)
//   }
  
}


