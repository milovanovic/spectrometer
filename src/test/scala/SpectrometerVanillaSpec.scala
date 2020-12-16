// SPDX-License-Identifier: Apache-2.0

package spectrometer

import dsptools._
import dsptools.numbers._

import chisel3._
import chisel3.util._
import chisel3.iotesters.Driver
import chisel3.experimental._

import chisel3.iotesters.PeekPokeTester
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy._

import org.scalatest.{FlatSpec, Matchers}

import plfg._
import nco._
import fft._
import magnitude._
import accumulator._

import java.io._

class SpectrometerVanillaTester
(
  dut: SpectrometerVanilla with SpectrometerVanillaPins,
  params: SpectrometerVanillaParameters,
  enablePlots: Boolean = false
) extends PeekPokeTester(dut.module) with AXI4MasterModel {

  def memAXI: AXI4Bundle = dut.ioMem.get
  val fftSize = params.fftParams.numPoints
  val binWithPeak = 24
  val startValue = (binWithPeak * 4 * params.ncoParams.tableSize)/fftSize
  val fftMagScala = SpectrometerTesterUtils.calcExpectedMagOut(fftSize, binWithPeak, fftSize, "jplMag")
  val numAccWin = 4 // Number of accumulated windows

  // plfg setup
  val segmentNumsArrayOffset = 6 * params.beatBytes
  val repeatedChirpNumsArrayOffset = segmentNumsArrayOffset + 4 * params.beatBytes
  val chirpOrdinalNumsArrayOffset = repeatedChirpNumsArrayOffset + 8 * params.beatBytes
    
  memWriteWord(params.plfgRAM.base, 0x24000000)
  memWriteWord(params.plfgAddress.base + 2*params.beatBytes, numAccWin*2) // number of frames
  memWriteWord(params.plfgAddress.base + 4*params.beatBytes, 1)           // number of chirps
  memWriteWord(params.plfgAddress.base + 5*params.beatBytes, startValue)  // start value
  memWriteWord(params.plfgAddress.base + segmentNumsArrayOffset, 1)       // number of segments for first chirp
  memWriteWord(params.plfgAddress.base + repeatedChirpNumsArrayOffset, 1) // determines number of repeated chirps
  memWriteWord(params.plfgAddress.base + chirpOrdinalNumsArrayOffset, 0)
  memWriteWord(params.plfgAddress.base + params.beatBytes, 0)             // set reset bit to zero
  memWriteWord(params.plfgAddress.base, 1)
  memWriteWord(params.magAddress.base, 2)                                 // configure jpl magnitude 
  // configure registers inside accumulator
  memWriteWord(params.accAddress.base, fftSize)                           // accDepth is equal to fftSize
  memWriteWord(params.accAddress.base + params.beatBytes, numAccWin)      // accumulate numAccWin fft windows
 
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
  val out = if (params.fftParams.useBitReverse || params.accParams.bitReversal) outSeq else SpectrometerTesterUtils.bitrevorder_data(outSeq)
  
  if (enablePlots) {
    SpectrometerTesterUtils.plot_fft(out.map(c =>c.toLong), "PLFG -> NCO -> FFT -> MAG -> ACC", fileName = "SpectrometerVanilla/SpectrometerVanillaPlot.pdf")
  }
}

class SpectrometerVanillaSpec extends FlatSpec with Matchers {

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
    
  it should "test SpectrometerVanilla module" in {
    val lazyDut = LazyModule(new SpectrometerVanilla(params) with SpectrometerVanillaPins)
    chisel3.iotesters.Driver.execute(Array("--backend-name", "verilator", "--target-dir", "test_run_dir/SpectrometerVanilla/", "--top-name", "SpectrometerVanilla"), () => lazyDut.module) {
    c => new SpectrometerVanillaTester(lazyDut, params, false)
    } should be (true)
  }
}
