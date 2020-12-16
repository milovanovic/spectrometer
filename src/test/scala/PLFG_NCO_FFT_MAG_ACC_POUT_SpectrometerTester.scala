// SPDX-License-Identifier: Apache-2.0

package spectrometer

import chisel3.iotesters.PeekPokeTester

import freechips.rocketchip.amba.axi4stream._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.diplomacy._

import breeze.math.Complex
import breeze.signal.fourierTr
import breeze.linalg._

import java.io._


//---------------------------------------------------------------------------------------------------------------------------------------------------------------
// PLFG -> NCO -> FFT -> MAG -> ACC -> parallel_out
//---------------------------------------------------------------------------------------------------------------------------------------------------------------
class PLFG_NCO_FFT_MAG_ACC_POUT_SpectrometerTester
(
  dut: SpectrometerTest with SpectrometerTestPins,
  params: SpectrometerTestParameters,
  enablePlot: Boolean = false,
  silentFail: Boolean = false
) extends PeekPokeTester(dut.module) with AXI4MasterModel {
  
  val mod = dut.module
  def memAXI: AXI4Bundle = dut.ioMem.get

  val fftSize = params.fftParams.numPoints
  val binWithPeak = 2
  val startValue = (binWithPeak * 4 * params.ncoParams.tableSize)/fftSize
  val fftMagScala = SpectrometerTesterUtils.calcExpectedMagOut(fftSize, binWithPeak, fftSize, "jplMag")
  val numAccuWin = 4 // Number of accumulated windows

  // plfg setup
  val segmentNumsArrayOffset = 6 * params.beatBytes
  val repeatedChirpNumsArrayOffset = segmentNumsArrayOffset + 4 * params.beatBytes
  val chirpOrdinalNumsArrayOffset = repeatedChirpNumsArrayOffset + 8 * params.beatBytes
    
  memWriteWord(params.plfgRAM.base, 0x24000000)
  memWriteWord(params.plfgAddress.base + 2*params.beatBytes, numAccuWin*2) // number of frames
  memWriteWord(params.plfgAddress.base + 4*params.beatBytes, 1)            // number of chirps
  memWriteWord(params.plfgAddress.base + 5*params.beatBytes, startValue)   // start value
  memWriteWord(params.plfgAddress.base + segmentNumsArrayOffset, 1)        // number of segments for first chirp
  memWriteWord(params.plfgAddress.base + repeatedChirpNumsArrayOffset, 1)  // determines number of repeated chirps
  memWriteWord(params.plfgAddress.base + chirpOrdinalNumsArrayOffset, 0) 
  memWriteWord(params.plfgAddress.base + params.beatBytes, 0)              // set reset bit to zero
  memWriteWord(params.plfgAddress.base, 1)                                 // enable bit becomes 1
  
  // Mux
  memWriteWord(params.plfgMuxAddress1.base, 0x0) // output0
  memWriteWord(params.ncoMuxAddress1.base, 0x0)  // output0
  memWriteWord(params.fftMuxAddress1.base, 0x0)  // output0
  memWriteWord(params.magMuxAddress1.base, 0x0)  // output0
  
  memWriteWord(params.plfgMuxAddress0.base, 0x0) // output0
  memWriteWord(params.ncoMuxAddress0.base, 0x0)  // output0
  memWriteWord(params.fftMuxAddress0.base, 0x0)  // output0
  memWriteWord(params.magMuxAddress0.base, 0x0)  // output0

  memWriteWord(params.outMuxAddress.base, 0x0)   // output0
  memWriteWord(params.magAddress.base, 0x2)      // set jpl magnitude
  
  memWriteWord(params.accAddress.base, params.fftParams.numPoints)  // set number of fft points
  memWriteWord(params.accAddress.base + 0x4, numAccuWin)            // set number of accumulated fft windows
  
  poke(dut.outStream.ready, true)

  var outSeq = Seq[Int]()
  var peekedVal: BigInt = 0
  
  // check only one fft window 
  while (outSeq.length < params.fftParams.numPoints * 2) {
    if (peek(dut.outStream.valid) == 1 && peek(dut.outStream.ready) == 1) {
      peekedVal = peek(dut.outStream.bits.data)
      outSeq = outSeq :+ peekedVal.toInt
    }
    step(1)
  }

  var realSeq = Seq[Int]()
  var tmpReal: Short = 0
  
  for (i <- 0 until outSeq.length by 2) {
    tmpReal = java.lang.Integer.parseInt(SpectrometerTesterUtils.asNdigitBinary(outSeq(i + 1), 8) ++ SpectrometerTesterUtils.asNdigitBinary(outSeq(i + 0), 8), 2).toShort
    realSeq = realSeq :+ tmpReal.toInt
  }
  // Output data
  val chiselFFTForPlot = realSeq.map(c => c.toLong).toSeq
   
  if (params.fftParams.useBitReverse || params.accParams.bitReversal) {
    SpectrometerTesterUtils.checkDataError(realSeq, fftMagScala, 3)
  }
  else {
    val brReal = SpectrometerTesterUtils.bitrevorder_data(realSeq)
    SpectrometerTesterUtils.checkDataError(brReal, fftMagScala, 3)
  }
  
  // Plot accelerator data
  if (enablePlot == true)
    SpectrometerTesterUtils.plot_fft(inputData = chiselFFTForPlot, plotName = "PLFG -> NCO -> FFT -> MAG -> ACC -> POUT", fileName = "SpectrometerTest/plfg_nco_fft_mag_acc_pout/plot.pdf")

  // Write output data to text file
  val file = new File("./test_run_dir/SpectrometerTest/plfg_nco_fft_mag_acc_pout/GoldenData.txt")
  val w = new BufferedWriter(new FileWriter(file))
  for (i <- 0 until realSeq.length ) {
    w.write(f"${realSeq(i)}%04x" + "\n")
  }
  w.close
}
