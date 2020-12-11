// SPDX-License-Identifier: Apache-2.0

package spectrometer

import chisel3.DontCare
import freechips.rocketchip.interrupts._
import dsptools._
import dsptools.numbers._
import chisel3.experimental._
import chisel3._
import chisel3.util._
import chisel3.iotesters.Driver

import chisel3.iotesters.PeekPokeTester
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

trait SpectrometerVanillaPins extends SpectrometerVanilla {
  def standaloneParams = AXI4BundleParameters(addrBits = 32, dataBits = 32, idBits = 1)
  val ioMem = mem.map { m => {
    val ioMemNode = BundleBridgeSource(() => AXI4Bundle(standaloneParams))
    m := BundleBridgeToAXI4(AXI4MasterPortParameters(Seq(AXI4MasterParameters("bundleBridgeToAXI4")))) := ioMemNode
    val ioMem = InModuleBody { ioMemNode.makeIO() }
    ioMem
  }}
  // Generate AXI-stream output
  val ioStreamNode = BundleBridgeSink[AXI4StreamBundle]()
  ioStreamNode := AXI4StreamToBundleBridge(AXI4StreamSlaveParameters()) := acc.streamNode
  val outStream = InModuleBody { ioStreamNode.makeIO() }
}

class SpectrometerVanillaTester
(
  dut: SpectrometerVanilla with SpectrometerVanillaPins,
  params: SpectrometerVanillaParameters,
  silentFail: Boolean = false
) extends PeekPokeTester(dut.module) with AXI4MasterModel {

  def memAXI: AXI4Bundle = dut.ioMem.get
  val numAccuWin = 4
  
  // plfg setup
  val segmentNumsArrayOffset = 6 * params.beatBytes
  val repeatedChirpNumsArrayOffset = segmentNumsArrayOffset + 4 * params.beatBytes
  val chirpOrdinalNumsArrayOffset = repeatedChirpNumsArrayOffset + 8 * params.beatBytes
  
  // configure plfg registers
  // peak is expected on frequency bin equal to startingPoint * (numOfPoints / (4*tableSize))
  memWriteWord(params.plfgRAM.base, 0x24000000)
  memWriteWord(params.plfgAddress.base + 2*params.beatBytes, numAccuWin*2) // number of frames
  memWriteWord(params.plfgAddress.base + 4*params.beatBytes, 1)            // number of chirps
  memWriteWord(params.plfgAddress.base + 5*params.beatBytes, 16)           // start value
  memWriteWord(params.plfgAddress.base + segmentNumsArrayOffset, 1)        // number of segments for first chirp
  memWriteWord(params.plfgAddress.base + repeatedChirpNumsArrayOffset, 1)  // determines number of repeated chirps
  memWriteWord(params.plfgAddress.base + chirpOrdinalNumsArrayOffset, 0) 
  memWriteWord(params.plfgAddress.base + params.beatBytes, 0)              // set reset bit to zero
  memWriteWord(params.plfgAddress.base, 1)                                 // enable bit becomes 1
  // configure registers inside fft
  memWriteWord(params.fftAddress.base, 10)                                 // 10 stages are active 
  // configure registers inside LogMagMux
  memWriteWord(params.magAddress.base, 2)                                  // configure jpl magnitude aproximation
  // configure registers inside accumulator
  memWriteWord(params.accAddress.base, 1024)                               // accDepth is 1024
  memWriteWord(params.accAddress.base + params.beatBytes, 2)               // accumulate 2 fft windows
 
  step(40)
  poke(dut.outStream.ready, true.B)

  var outSeq = Seq[Int]()
  var peekedVal: BigInt = 0
   
  while (outSeq.length < params.fftParams.numPoints) {
    if (peek(dut.outStream.valid) == 1 && peek(dut.outStream.ready) == 1) {
      peekedVal = peek(dut.outStream.bits.data)
      outSeq = outSeq :+ peekedVal.toInt
    }
    step(1)
  }
  val out = if (params.fftParams.useBitReverse) outSeq else SpectrometerTesterUtils.bitrevorder_data(outSeq)
  //SpectrometerTesterUtils.plot_fft(out.map(c =>    c.toLong), "PLFG -> NCO -> FFT -> MAG -> ACC", fileName = "SpectrometerVanilla/SpectrometerVanillaPlot.pdf")
  
//   val pwMagnitude = new PrintWriter(new File("printWriterRes/SpectrometerVanillaOut.txt"))
//   outSeq.foreach(x => pwMagnitude.println(x.toDouble.toString))
//   pwMagnitude.close
}

class SpectrometerVanillaSpec extends FlatSpec with Matchers {
  implicit val p: Parameters = Parameters.empty

  val params = SpectrometerVanillaParameters (
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
      numPoints = 1024,
      useBitReverse  = false,
      runTime = true,
      numAddPipes = 1,
      numMulPipes = 1,
      expandLogic = Array.fill(log2Up(1024))(0),
      keepMSBorLSB = Array.fill(log2Up(1024))(true),
      minSRAMdepth = 1024,
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
      proto    = FixedPoint(16.W, 0.BP),
      protoAcc = FixedPoint(32.W, 0.BP),
    ),
    plfgAddress     = AddressSet(0x30000000, 0xFF),
    plfgRAM         = AddressSet(0x30001000, 0xFFF),
    ncoAddress      = AddressSet(0x30000300, 0xF),
    fftAddress      = AddressSet(0x30000100, 0xFF),
    magAddress      = AddressSet(0x30000200, 0xFF),
    accAddress      = AddressSet(0x30000310, 0xF),
    accQueueBase    = 0x30002000,
    beatBytes      = 4)
    
  it should "test spectrometer vanilla module" ignore {
    val lazyDut = LazyModule(new SpectrometerVanilla(params) with SpectrometerVanillaPins)
    chisel3.iotesters.Driver.execute(Array("-tiwv", "-tbn", "verilator", "-tivsuv", "--target-dir", "test_run_dir/SpectrometerVanilla/", "--top-name", "SpectrometerVanilla"), () => lazyDut.module) {
    c => new SpectrometerVanillaTester(lazyDut, params, true)
    } should be (true)
  }
}
