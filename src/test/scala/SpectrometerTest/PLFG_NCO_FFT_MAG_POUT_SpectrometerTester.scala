// SPDX-License-Identifier: Apache-2.0

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

//---------------------------------------------------------------------------------------------------------------------------------------------------------------
// PLFG -> NCO -> FFT -> MAG -> parallel_out
//---------------------------------------------------------------------------------------------------------------------------------------------------------------
class PLFG_NCO_FFT_MAG_POUT_SpectrometerTester
(
  dut: SpectrometerTest with SpectrometerTestPins,
  params: SpectrometerTestParameters,
  enablePlot: Boolean = false,
  silentFail: Boolean = false
) extends PeekPokeTester(dut.module) with AXI4MasterModel {
  
  val mod = dut.module
  def memAXI: AXI4Bundle = dut.ioMem.get

  // plfg setup
  val segmentNumsArrayOffset = 6 * params.beatBytes
  val repeatedChirpNumsArrayOffset = segmentNumsArrayOffset + 4 * params.beatBytes
  val chirpOrdinalNumsArrayOffset = repeatedChirpNumsArrayOffset + 8 * params.beatBytes
  
  val fftSize = params.fftParams.numPoints
  val binWithPeak = 2
  val startValue = (binWithPeak * 4 * params.ncoParams.tableSize)/fftSize
  val fftMagScala = SpectrometerTesterUtils.calcExpectedMagOut(fftSize, binWithPeak, fftSize)
  
  
  memWriteWord(params.plfgRAM.base, 0x24000000)
  memWriteWord(params.plfgAddress.base + 2*params.beatBytes, 4)            // number of frames
  memWriteWord(params.plfgAddress.base + 4*params.beatBytes, 1)            // number of chirps
  //memWriteWord(params.plfgAddress.base + 5*params.beatBytes, 1)          // start value
  memWriteWord(params.plfgAddress.base + 5*params.beatBytes, startValue)           // start value
  memWriteWord(params.plfgAddress.base + segmentNumsArrayOffset, 1)        // number of segments for first chirp
  memWriteWord(params.plfgAddress.base + repeatedChirpNumsArrayOffset, 1)  // determines number of repeated chirps
  memWriteWord(params.plfgAddress.base + chirpOrdinalNumsArrayOffset, 0) 
  memWriteWord(params.plfgAddress.base + params.beatBytes, 0)              // set reset bit to zero
  memWriteWord(params.plfgAddress.base, 1)                                 // enable bit becomes 1
  
  // Mux
  memWriteWord(params.plfgMuxAddress1.base,       0x0) // output0
  memWriteWord(params.ncoMuxAddress1.base,       0x0)  // output0
  memWriteWord(params.fftMuxAddress1.base,       0x0)  // output0
  memWriteWord(params.magMuxAddress1.base + 0x4, 0x0)  // output1

  memWriteWord(params.plfgMuxAddress0.base,       0x0) // output0
  memWriteWord(params.ncoMuxAddress0.base,       0x0)  // output0
  memWriteWord(params.fftMuxAddress0.base,       0x0)  // output0
  memWriteWord(params.magMuxAddress0.base + 0x4, 0x0)  // output1

  memWriteWord(params.outMuxAddress.base,       0x1)   // output0
  memWriteWord(params.magAddress.base, 0x2)            // set jpl magnitude
  
  poke(dut.outStream.ready, true.B)

  var outSeq = Seq[Int]()
  var peekedVal: BigInt = 0
  
  // check only one fft window
  while (outSeq.length < params.fftParams.numPoints * 4) {
    if (peek(dut.outStream.valid) == 1 && peek(dut.outStream.ready) == 1) {
      peekedVal = peek(dut.outStream.bits.data)
      outSeq = outSeq :+ peekedVal.toInt
    }
    step(1)
  }

  var realSeq = Seq[Int]()
  var imagSeq = Seq[Int]()
  var tmpReal: Short = 0
  var tmpImag: Short = 0
  
  for (i <- 0 until outSeq.length by 4) {
    tmpReal = java.lang.Integer.parseInt(SpectrometerTesterUtils.asNdigitBinary(outSeq(i + 3), 8) ++ SpectrometerTesterUtils.asNdigitBinary(outSeq(i + 2), 8), 2).toShort
    tmpImag = java.lang.Long.parseLong(SpectrometerTesterUtils.asNdigitBinary(outSeq(i + 1), 8)   ++ SpectrometerTesterUtils.asNdigitBinary(outSeq(i), 8), 2).toShort
    realSeq = realSeq :+ tmpReal.toInt
    imagSeq = imagSeq :+ tmpImag.toInt
  }
  
 imagSeq.foreach( c => println(c.toString))
 fftMagScala.foreach( c => println(c.toString))
 
 
//  if (params.fftParams.useBitReverse) {
//     SpectrometerTesterUtils.checkDataError(imagSeq, fftMagScala, 3)
//   }
//   else {
//     val brImag = SpectrometerTesterUtils.bitrevorder_data(imagSeq)
//     SpectrometerTesterUtils.checkDataError(brImag, fftMagScala, 3)
//   }
  
  if (enablePlot) {
    SpectrometerTesterUtils.plot_data(inputData = imagSeq.map(c => c.toInt), plotName = "PLFG -> NCO -> FFT -> MAG -> POUT", fileName = "SpectrometerTest/plfg_nco_fft_mag_pout/plot.pdf")
  }

  // Write output data to text file
  val file = new File("./test_run_dir/SpectrometerTest/plfg_nco_fft_mag_pout/data.txt")
  val w = new BufferedWriter(new FileWriter(file))
  for (i <- 0 until realSeq.length ) {
    w.write(f"${realSeq(i)}%04x" + f"${imagSeq(i)}%04x" + "\n")
  }
  w.close
}
